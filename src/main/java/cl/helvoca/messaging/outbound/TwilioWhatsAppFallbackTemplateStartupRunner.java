package cl.helvoca.messaging.outbound;

import cl.helvoca.telephony.twilio.TwilioProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Component
public class TwilioWhatsAppFallbackTemplateStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppFallbackTemplateStartupRunner.class);

    static final String FRIENDLY_NAME = "helvoca_setup_confirmation_v2_20260918";
    static final String APPROVAL_NAME = "helvoca_setup_confirmation_v2_20260918";
    static final String BODY =
            "Solicitaste configurar tu canal de WhatsApp en Helvoca. La configuración quedó completada correctamente.";

    private static final String CONTENT_API = "https://content.twilio.com/v1/Content";
    private static final String CONTENT_LIST = CONTENT_API + "?PageSize=100";
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final Pattern CONTENT_SID = Pattern.compile("^HX[0-9a-fA-F]{32}$");
    private static final int START_DELAY_SECONDS = 10;

    private final boolean enabled;
    private final int maxPolls;
    private final int pollSeconds;
    private final TwilioProperties twilio;
    private final HttpClient http;

    @Autowired
    public TwilioWhatsAppFallbackTemplateStartupRunner(
            @Value("${HELVOCA_WHATSAPP_FALLBACK_TEMPLATE_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_FALLBACK_TEMPLATE_MAX_POLLS:120}") int maxPolls,
            @Value("${HELVOCA_WHATSAPP_FALLBACK_TEMPLATE_POLL_SECONDS:30}") int pollSeconds,
            TwilioProperties twilio) {
        this(enabled, maxPolls, pollSeconds, twilio,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    TwilioWhatsAppFallbackTemplateStartupRunner(boolean enabled,
                                                int maxPolls,
                                                int pollSeconds,
                                                TwilioProperties twilio,
                                                HttpClient http) {
        this.enabled = enabled;
        this.maxPolls = Math.max(1, Math.min(maxPolls, 240));
        this.pollSeconds = Math.max(5, Math.min(pollSeconds, 300));
        this.twilio = twilio;
        this.http = http;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (!validCredentials()) {
            log.error("WHATSAPP_FALLBACK_TEMPLATE blocked: invalid Twilio credentials");
            return;
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "whatsapp-fallback-template");
            t.setDaemon(true);
            return t;
        });
        AtomicInteger attempts = new AtomicInteger();

        class PollTask implements Runnable {
            @Override
            public void run() {
                int attempt = attempts.incrementAndGet();
                try {
                    TemplateState state = prepareOnce();
                    log.info(
                            "WHATSAPP_FALLBACK_TEMPLATE sid={} status={} category={} contentType={} rejectionReason={} attempt={}",
                            state.contentSid(),
                            state.status(),
                            state.category(),
                            state.contentType(),
                            sanitize(state.rejectionReason()),
                            attempt
                    );
                    if (terminal(state.status()) || attempt >= maxPolls) {
                        scheduler.shutdown();
                    } else {
                        scheduler.schedule(this, pollSeconds, TimeUnit.SECONDS);
                    }
                } catch (Exception e) {
                    log.error("WHATSAPP_FALLBACK_TEMPLATE failed reason={}", rootMessage(e));
                    scheduler.shutdown();
                }
            }
        }

        log.info("WHATSAPP_FALLBACK_TEMPLATE armed; starting in {} seconds", START_DELAY_SECONDS);
        scheduler.schedule(new PollTask(), START_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    TemplateState prepareOnce() throws Exception {
        if (!validCredentials()) {
            throw new IllegalStateException("Invalid Twilio credentials");
        }

        String sid = findExistingContentSid();
        if (sid == null) {
            sid = createContent();
            log.info("WHATSAPP_FALLBACK_TEMPLATE created sid={}", sid);
        }

        TemplateState state = fetchApproval(sid);
        if ("unsubmitted".equals(state.status())) {
            state = submitApproval(sid);
            log.info("WHATSAPP_FALLBACK_TEMPLATE approval submitted sid={} status={}", sid, state.status());
        }
        return state;
    }

    private String findExistingContentSid() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(CONTENT_LIST))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", basicAuthorization())
                .GET()
                .build());
        require2xx(response, "Twilio fallback content list");

        JSONArray contents = new JSONObject(response.body()).optJSONArray("contents");
        if (contents == null) return null;
        for (int i = 0; i < contents.length(); i++) {
            JSONObject content = contents.optJSONObject(i);
            if (content == null || !FRIENDLY_NAME.equals(content.optString("friendly_name", ""))) continue;
            String sid = content.optString("sid", "").trim();
            if (!CONTENT_SID.matcher(sid).matches()) {
                throw new IllegalStateException("Twilio returned invalid fallback Content SID");
            }
            return sid;
        }
        return null;
    }

    private String createContent() throws Exception {
        JSONObject payload = new JSONObject()
                .put("friendly_name", FRIENDLY_NAME)
                .put("language", "es")
                .put("types", new JSONObject()
                        .put("twilio/text", new JSONObject().put("body", BODY)));

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(CONTENT_API))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", basicAuthorization())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build());
        require2xx(response, "Twilio fallback content create");

        String sid = new JSONObject(response.body()).optString("sid", "").trim();
        if (!CONTENT_SID.matcher(sid).matches()) {
            throw new IllegalStateException("Twilio fallback content create returned no Content SID");
        }
        return sid;
    }

    private TemplateState fetchApproval(String sid) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(CONTENT_API + "/" + sid + "/ApprovalRequests"))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", basicAuthorization())
                .GET()
                .build());

        if (response.statusCode() == 404) {
            return new TemplateState(sid, "unsubmitted", "", "", "");
        }
        require2xx(response, "Twilio fallback approval fetch");

        JSONObject whatsapp = new JSONObject(response.body()).optJSONObject("whatsapp");
        if (whatsapp == null) {
            return new TemplateState(sid, "unsubmitted", "", "", "");
        }
        return new TemplateState(
                sid,
                normalize(whatsapp.optString("status", "unsubmitted")),
                whatsapp.optString("category", ""),
                whatsapp.optString("content_type", ""),
                whatsapp.optString("rejection_reason", "")
        );
    }

    private TemplateState submitApproval(String sid) throws Exception {
        JSONObject payload = new JSONObject()
                .put("name", APPROVAL_NAME)
                .put("category", "UTILITY");

        HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(CONTENT_API + "/" + sid + "/ApprovalRequests/whatsapp"))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", basicAuthorization())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build());
        require2xx(response, "Twilio fallback approval submit");

        JSONObject json = new JSONObject(response.body());
        return new TemplateState(
                sid,
                normalize(json.optString("status", "received")),
                json.optString("category", "UTILITY"),
                json.optString("content_type", "twilio/text"),
                json.optString("rejection_reason", "")
        );
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private boolean validCredentials() {
        return twilio != null
                && twilio.getAccountSid() != null
                && ACCOUNT_SID.matcher(twilio.getAccountSid().trim()).matches()
                && twilio.getAuthToken() != null
                && !twilio.getAuthToken().isBlank();
    }

    private String basicAuthorization() {
        String credentials = twilio.getAccountSid().trim() + ":" + twilio.getAuthToken().trim();
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static void require2xx(HttpResponse<String> response, String action) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(action + " failed with HTTP " + response.statusCode());
        }
    }

    private static boolean terminal(String status) {
        String value = normalize(status);
        return "approved".equals(value)
                || "rejected".equals(value)
                || "paused".equals(value)
                || "disabled".equals(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String sanitized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return sanitized.length() <= 300 ? sanitized : sanitized.substring(0, 300);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null) return "unknown";
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    record TemplateState(String contentSid,
                         String status,
                         String category,
                         String contentType,
                         String rejectionReason) { }
}
