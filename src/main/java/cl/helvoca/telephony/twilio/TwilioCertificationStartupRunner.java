package cl.helvoca.telephony.twilio;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Explicitly opt-in one-shot real-call certification harness.
 *
 * It is intentionally disabled by default and exists only so a deliberately
 * authorized production certification can be initiated from Railway without
 * requiring a human to click Twilio Console. The switch must be turned off
 * immediately after the authorized run.
 */
@Component
public class TwilioCertificationStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioCertificationStartupRunner.class);
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final AtomicBoolean FIRED = new AtomicBoolean(false);
    private static final int START_DELAY_SECONDS = 10;

    private final boolean enabled;
    private final String accountSid;
    private final String authToken;
    private final String from;
    private final String to;
    private final String publicBaseUrl;
    private final int maxSeconds;
    private final TwilioCallControl callControl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public TwilioCertificationStartupRunner(
            @Value("${TWILIO_CERTIFICATION_CALL_ON_STARTUP:false}") boolean enabled,
            @Value("${TWILIO_ACCOUNT_SID:}") String accountSid,
            @Value("${TWILIO_AUTH_TOKEN:}") String authToken,
            @Value("${TWILIO_TEST_FROM:}") String from,
            @Value("${TWILIO_TEST_TO:}") String to,
            @Value("${TWILIO_PUBLIC_BASE_URL:}") String publicBaseUrl,
            @Value("${TWILIO_CERTIFICATION_MAX_SECONDS:75}") int maxSeconds,
            TwilioCallControl callControl) {
        this.enabled = enabled;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.from = from;
        this.to = to;
        this.publicBaseUrl = publicBaseUrl;
        this.maxSeconds = Math.max(20, Math.min(maxSeconds, 180));
        this.callControl = callControl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || !FIRED.compareAndSet(false, true)) return;
        if (!validConfiguration()) {
            log.error("TWILIO_CERTIFICATION_CALL blocked: invalid/missing Twilio certification configuration");
            return;
        }

        ScheduledExecutorService kickoff = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "twilio-certification-kickoff");
            t.setDaemon(true);
            return t;
        });
        log.info("TWILIO_CERTIFICATION_CALL armed; starting in {} seconds after deployment cutover",
                START_DELAY_SECONDS);
        kickoff.schedule(() -> {
            try {
                String callSid = createCall();
                log.info("TWILIO_CERTIFICATION_CALL CREATED call={} to={} max_seconds={}",
                        callSid, mask(to), maxSeconds);
                scheduleSafetyHangup(callSid);
            } catch (Exception e) {
                log.error("TWILIO_CERTIFICATION_CALL FAILED reason={}", rootMessage(e));
            } finally {
                kickoff.shutdown();
            }
        }, START_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private String createCall() throws Exception {
        String base = trimTrailingSlash(publicBaseUrl.trim());
        String voiceUrl = base + "/webhooks/v1/twilio/outbound-test";
        String statusUrl = base + "/webhooks/v1/twilio/status";

        String body = form("To", to.trim())
                + "&" + form("From", from.trim())
                + "&" + form("Url", voiceUrl)
                + "&" + form("Method", "POST")
                + "&" + form("StatusCallback", statusUrl)
                + "&" + form("StatusCallbackMethod", "POST")
                + "&" + form("StatusCallbackEvent", "initiated")
                + "&" + form("StatusCallbackEvent", "ringing")
                + "&" + form("StatusCallbackEvent", "answered")
                + "&" + form("StatusCallbackEvent", "completed")
                + "&" + form("Timeout", "25");

        String basic = Base64.getEncoder().encodeToString(
                (accountSid.trim() + ":" + authToken.trim()).getBytes(StandardCharsets.UTF_8));
        URI uri = URI.create("https://api.twilio.com/2010-04-01/Accounts/"
                + accountSid.trim() + "/Calls.json");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Twilio create call http=" + response.statusCode()
                    + " body=" + truncate(response.body()));
        }
        JSONObject json = new JSONObject(response.body());
        String sid = json.optString("sid", "");
        if (!sid.startsWith("CA")) throw new IllegalStateException("Twilio create call returned no CallSid");
        return sid;
    }

    private void scheduleSafetyHangup(String callSid) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "twilio-certification-hangup");
            t.setDaemon(true);
            return t;
        });
        scheduler.schedule(() -> {
            boolean ended = callControl.hangup(accountSid.trim(), callSid);
            log.info("TWILIO_CERTIFICATION_CALL SAFETY_HANGUP call={} accepted={}", callSid, ended);
            scheduler.shutdown();
        }, maxSeconds, TimeUnit.SECONDS);
    }

    private boolean validConfiguration() {
        return accountSid != null && ACCOUNT_SID.matcher(accountSid.trim()).matches()
                && authToken != null && !authToken.isBlank()
                && from != null && E164.matcher(from.trim()).matches()
                && to != null && E164.matcher(to.trim()).matches()
                && publicBaseUrl != null && publicBaseUrl.trim().startsWith("https://");
    }

    private static String form(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String value) {
        String out = value;
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 5) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 400 ? value : value.substring(0, 400);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null) return "unknown";
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
