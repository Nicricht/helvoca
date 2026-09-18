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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
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
public class TwilioWhatsAppProductionCertificationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppProductionCertificationRunner.class);

    private static final String TEMPLATE_FRIENDLY_NAME = "helvoca_setup_confirmation_v1_20260918";
    private static final String TEMPLATE_APPROVAL_NAME = "helvoca_setup_confirmation_v1_20260918";
    private static final String TEMPLATE_BODY =
            "Tu canal de WhatsApp fue configurado correctamente. Responde OK para confirmar que recibiste este mensaje.";

    private static final String CONTENT_API = "https://content.twilio.com/v1/Content";
    private static final String CONTENT_AND_APPROVALS =
            "https://content.twilio.com/v1/ContentAndApprovals?PageSize=100";
    private static final String MESSAGES_API = "https://api.twilio.com/2010-04-01/Accounts/";

    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final Pattern CONTENT_SID = Pattern.compile("^HX[0-9a-fA-F]{32}$");
    private static final Pattern MESSAGE_SID = Pattern.compile("^SM[0-9a-fA-F]{32}$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern RUN_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$");

    private static final int START_DELAY_SECONDS = 10;

    private final boolean enabled;
    private final String runId;
    private final String sender;
    private final String recipient;
    private final int maxApprovalPolls;
    private final int approvalPollSeconds;
    private final TwilioProperties twilio;
    private final JdbcTemplate jdbc;
    private final HttpClient http;

    @Autowired
    public TwilioWhatsAppProductionCertificationRunner(
            @Value("${HELVOCA_WHATSAPP_CERTIFICATION_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_CERTIFICATION_RUN_ID:}") String runId,
            @Value("${HELVOCA_WHATSAPP_SENDER_E164:}") String sender,
            @Value("${HELVOCA_WHATSAPP_CERTIFICATION_TO:}") String recipient,
            @Value("${HELVOCA_WHATSAPP_CERTIFICATION_MAX_POLLS:120}") int maxApprovalPolls,
            @Value("${HELVOCA_WHATSAPP_CERTIFICATION_POLL_SECONDS:30}") int approvalPollSeconds,
            TwilioProperties twilio,
            JdbcTemplate jdbc) {
        this(enabled, runId, sender, recipient, maxApprovalPolls, approvalPollSeconds, twilio, jdbc,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    TwilioWhatsAppProductionCertificationRunner(boolean enabled,
                                                String runId,
                                                String sender,
                                                String recipient,
                                                int maxApprovalPolls,
                                                int approvalPollSeconds,
                                                TwilioProperties twilio,
                                                JdbcTemplate jdbc,
                                                HttpClient http) {
        this.enabled = enabled;
        this.runId = trim(runId);
        this.sender = trim(sender);
        this.recipient = trim(recipient);
        this.maxApprovalPolls = Math.max(1, Math.min(maxApprovalPolls, 240));
        this.approvalPollSeconds = Math.max(5, Math.min(approvalPollSeconds, 300));
        this.twilio = twilio;
        this.jdbc = jdbc;
        this.http = http;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (!validConfiguration()) {
            log.error("WHATSAPP_PRODUCTION_CERTIFICATION blocked: invalid configuration");
            return;
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "whatsapp-production-certification");
            t.setDaemon(true);
            return t;
        });
        AtomicInteger attempts = new AtomicInteger();

        class PollTask implements Runnable {
            @Override
            public void run() {
                int attempt = attempts.incrementAndGet();
                try {
                    CertificationResult result = executeOnce();
                    log.info(
                            "WHATSAPP_PRODUCTION_CERTIFICATION state template={} approval={} message={} status={} attempt={}",
                            safe(result.templateSid()),
                            safe(result.approvalStatus()),
                            safe(result.messageSid()),
                            safe(result.messageStatus()),
                            attempt
                    );

                    if (terminal(result) || attempt >= maxApprovalPolls) {
                        if (!terminal(result)) {
                            log.warn("WHATSAPP_PRODUCTION_CERTIFICATION stopped awaiting approval after attempts={}", attempt);
                        }
                        scheduler.shutdown();
                        return;
                    }
                    scheduler.schedule(this, approvalPollSeconds, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.error("WHATSAPP_PRODUCTION_CERTIFICATION failed reason={}", rootMessage(e));
                    scheduler.shutdown();
                }
            }
        }

        log.info("WHATSAPP_PRODUCTION_CERTIFICATION armed; starting in {} seconds", START_DELAY_SECONDS);
        scheduler.schedule(new PollTask(), START_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    CertificationResult executeOnce() throws Exception {
        if (!validConfiguration()) {
            throw new IllegalStateException("Invalid WhatsApp certification configuration");
        }

        TemplateInfo template = findTemplate();
        if (template == null) {
            String contentSid = createTemplate();
            submitApproval(contentSid);
            return new CertificationResult(contentSid, "received", null, "awaiting_approval");
        }

        String approval = normalizeStatus(template.approvalStatus());
        if (approval.isBlank() || "unsubmitted".equals(approval)) {
            submitApproval(template.sid());
            return new CertificationResult(template.sid(), "received", null, "awaiting_approval");
        }
        if ("received".equals(approval) || "pending".equals(approval)) {
            return new CertificationResult(template.sid(), approval, null, "awaiting_approval");
        }
        if (!"approved".equals(approval)) {
            return new CertificationResult(template.sid(), approval, null, "template_" + approval);
        }

        if (!claimRealSend()) {
            return new CertificationResult(template.sid(), approval, null, "blocked_duplicate");
        }

        String messageSid = sendApprovedTemplate(template.sid());
        String messageStatus = waitForMessageStatus(messageSid);
        return new CertificationResult(template.sid(), approval, messageSid, messageStatus);
    }

    private TemplateInfo findTemplate() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(CONTENT_AND_APPROVALS))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", basicAuthorization())
                .GET()
                .build());
        require2xx(response, "Twilio template lookup");

        JSONArray contents = new JSONObject(response.body()).optJSONArray("contents");
        if (contents == null) return null;

        for (int i = 0; i < contents.length(); i++) {
            JSONObject content = contents.optJSONObject(i);
            if (content == null || !TEMPLATE_FRIENDLY_NAME.equals(content.optString("friendly_name", ""))) continue;

            String sid = content.optString("sid", "").trim();
            if (!CONTENT_SID.matcher(sid).matches()) {
                throw new IllegalStateException("Twilio returned invalid Content SID");
            }
            JSONObject approvals = content.optJSONObject("approvals");
            JSONObject whatsapp = approvals == null ? null : approvals.optJSONObject("whatsapp");
            String status = whatsapp == null ? "unsubmitted" : whatsapp.optString("status", "unsubmitted");
            return new TemplateInfo(sid, status);
        }
        return null;
    }

    private String createTemplate() throws Exception {
        JSONObject payload = new JSONObject()
                .put("friendly_name", TEMPLATE_FRIENDLY_NAME)
                .put("language", "es")
                .put("types", new JSONObject()
                        .put("twilio/text", new JSONObject().put("body", TEMPLATE_BODY)));

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(CONTENT_API))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", basicAuthorization())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build());
        require2xx(response, "Twilio template create");

        String sid = new JSONObject(response.body()).optString("sid", "").trim();
        if (!CONTENT_SID.matcher(sid).matches()) {
            throw new IllegalStateException("Twilio template create returned no Content SID");
        }
        log.info("WHATSAPP_PRODUCTION_CERTIFICATION template created sid={}", sid);
        return sid;
    }

    private void submitApproval(String contentSid) throws Exception {
        JSONObject payload = new JSONObject()
                .put("name", TEMPLATE_APPROVAL_NAME)
                .put("category", "UTILITY");

        HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(CONTENT_API + "/" + contentSid + "/ApprovalRequests/WhatsApp"))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", basicAuthorization())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build());
        require2xx(response, "Twilio WhatsApp approval submit");
        String status = new JSONObject(response.body()).optString("status", "received");
        log.info("WHATSAPP_PRODUCTION_CERTIFICATION approval submitted sid={} status={}", contentSid, status);
    }

    private boolean claimRealSend() {
        int inserted = jdbc.update("""
                INSERT INTO twilio_certification_run (run_id, direction, claimed_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (run_id) DO NOTHING
                """, runId, "whatsapp");
        return inserted == 1;
    }

    private String sendApprovedTemplate(String contentSid) throws Exception {
        String body = form("To", "whatsapp:" + recipient)
                + "&" + form("From", "whatsapp:" + sender)
                + "&" + form("ContentSid", contentSid);

        String accountSid = twilio.getAccountSid().trim();
        HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(MESSAGES_API + accountSid + "/Messages.json"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", basicAuthorization())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        require2xx(response, "Twilio WhatsApp certification send");

        JSONObject json = new JSONObject(response.body());
        String sid = json.optString("sid", "").trim();
        if (!MESSAGE_SID.matcher(sid).matches()) {
            throw new IllegalStateException("Twilio WhatsApp certification send returned no MessageSid");
        }
        log.info("WHATSAPP_PRODUCTION_CERTIFICATION SENT sid={} from={} to={}",
                sid, mask(sender), mask(recipient));
        return sid;
    }

    private String waitForMessageStatus(String messageSid) throws Exception {
        String accountSid = twilio.getAccountSid().trim();
        String lastStatus = "unknown";

        for (int i = 0; i < 20; i++) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(
                            URI.create(MESSAGES_API + accountSid + "/Messages/" + messageSid + ".json"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", basicAuthorization())
                    .GET()
                    .build());
            require2xx(response, "Twilio WhatsApp message status");

            JSONObject json = new JSONObject(response.body());
            lastStatus = normalizeStatus(json.optString("status", "unknown"));
            if ("delivered".equals(lastStatus) || "read".equals(lastStatus)
                    || "failed".equals(lastStatus) || "undelivered".equals(lastStatus)) {
                log.info("WHATSAPP_PRODUCTION_CERTIFICATION DELIVERY sid={} status={}", messageSid, lastStatus);
                return lastStatus;
            }
            if (i < 19) Thread.sleep(3000);
        }
        return lastStatus;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private void require2xx(HttpResponse<String> response, String action) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(action + " failed with HTTP " + response.statusCode());
        }
    }

    private boolean validConfiguration() {
        return enabled
                && twilio != null
                && twilio.getAccountSid() != null
                && ACCOUNT_SID.matcher(twilio.getAccountSid().trim()).matches()
                && twilio.getAuthToken() != null
                && !twilio.getAuthToken().isBlank()
                && RUN_ID.matcher(runId).matches()
                && E164.matcher(sender).matches()
                && E164.matcher(recipient).matches();
    }

    private String basicAuthorization() {
        String credentials = twilio.getAccountSid().trim() + ":" + twilio.getAuthToken().trim();
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean terminal(CertificationResult result) {
        String status = normalizeStatus(result.messageStatus());
        return "delivered".equals(status)
                || "read".equals(status)
                || "failed".equals(status)
                || "undelivered".equals(status)
                || "blocked_duplicate".equals(status)
                || status.startsWith("template_rejected")
                || status.startsWith("template_paused")
                || status.startsWith("template_disabled");
    }

    private static String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String form(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 5) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null) return "unknown";
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    record TemplateInfo(String sid, String approvalStatus) { }

    record CertificationResult(String templateSid,
                               String approvalStatus,
                               String messageSid,
                               String messageStatus) { }
}
