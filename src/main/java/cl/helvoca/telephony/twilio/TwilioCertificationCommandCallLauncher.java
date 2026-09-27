package cl.helvoca.telephony.twilio;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.regex.Pattern;

@Component
public class TwilioCertificationCommandCallLauncher {
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final TwilioProperties twilio;
    private final String from;
    private final String to;
    private final String allowedTo;
    private final String forbiddenTo;
    private final int maxSeconds;
    private final TwilioCallControl callControl;
    private final HttpClient http;

    @Autowired
    public TwilioCertificationCommandCallLauncher(
            TwilioProperties twilio,
            @Value("${TWILIO_TEST_FROM:}") String from,
            @Value("${TWILIO_TEST_TO:}") String to,
            @Value("${TWILIO_CERTIFICATION_ALLOWED_TO:}") String allowedTo,
            @Value("${TWILIO_CERTIFICATION_FORBIDDEN_TO:}") String forbiddenTo,
            @Value("${TWILIO_CERTIFICATION_MAX_SECONDS:150}") int maxSeconds,
            TwilioCallControl callControl) {
        this(twilio, from, to, allowedTo, forbiddenTo, maxSeconds, callControl,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    TwilioCertificationCommandCallLauncher(
            TwilioProperties twilio,
            String from,
            String to,
            String allowedTo,
            String forbiddenTo,
            int maxSeconds,
            TwilioCallControl callControl,
            HttpClient http) {
        this.twilio = twilio;
        this.from = from;
        this.to = to;
        this.allowedTo = allowedTo;
        this.forbiddenTo = forbiddenTo;
        this.maxSeconds = Math.max(20, Math.min(maxSeconds, 180));
        this.callControl = callControl;
        this.http = http;
    }

    public String launch(String callbackToken) throws Exception {
        if (!validConfiguration()) {
            throw new IllegalStateException("invalid/missing Twilio certification command configuration");
        }
        if (!TwilioCertificationCommandStore.validToken(callbackToken)) {
            throw new IllegalArgumentException("invalid certification callback token");
        }

        String voiceUrl = twilio.absoluteWebhook("/webhooks/v1/twilio/inbound-certification")
                + "?token=" + URLEncoder.encode(callbackToken, StandardCharsets.UTF_8);
        String statusUrl = twilio.absoluteWebhook("/webhooks/v1/twilio/status");

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
                (twilio.getAccountSid().trim() + ":" + twilio.getAuthToken().trim())
                        .getBytes(StandardCharsets.UTF_8));
        URI uri = URI.create("https://api.twilio.com/2010-04-01/Accounts/"
                + twilio.getAccountSid().trim() + "/Calls.json");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Twilio create call http=" + response.statusCode());
        }

        String callSid = new JSONObject(response.body()).optString("sid", "");
        if (!TwilioCertificationCommandStore.validCallSid(callSid)) {
            throw new IllegalStateException("Twilio create call returned no valid CallSid");
        }
        scheduleSafetyHangup(callSid);
        return callSid;
    }

    boolean validConfiguration() {
        return twilio.hasAccountSid()
                && twilio.hasAuthToken()
                && twilio.hasSecurePublicBaseUrl()
                && from != null
                && E164.matcher(from.trim()).matches()
                && to != null
                && E164.matcher(to.trim()).matches()
                && TwilioCertificationStartupRunner.isAllowedTarget(to, allowedTo)
                && !TwilioCertificationStartupRunner.isForbiddenTarget(to, forbiddenTo);
    }

    private void scheduleSafetyHangup(String callSid) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "twilio-certification-command-hangup");
            t.setDaemon(true);
            return t;
        });
        scheduler.schedule(() -> {
            callControl.hangup(twilio.getAccountSid().trim(), callSid);
            scheduler.shutdown();
        }, maxSeconds, TimeUnit.SECONDS);
    }

    private static String form(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
