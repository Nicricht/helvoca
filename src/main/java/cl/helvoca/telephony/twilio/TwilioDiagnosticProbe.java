package cl.helvoca.telephony.twilio;

import org.json.JSONArray;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Temporary, read-only production diagnostic. It never creates or updates calls.
 * Enable only with TWILIO_DIAGNOSTIC_PROBE=true, inspect logs once, then disable/remove it.
 */
@Component
public class TwilioDiagnosticProbe implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioDiagnosticProbe.class);
    private static final Pattern PHONE = Pattern.compile("\\+\\d{7,15}");
    private static final Pattern ROUTE = Pattern.compile("(?i)(x-recepvoz-route(?:=|%3D))[^&\\s]+", Pattern.CASE_INSENSITIVE);

    private final TwilioProperties properties;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${TWILIO_DIAGNOSTIC_PROBE:false}")
    private boolean enabled;

    @Value("${TWILIO_ACCOUNT_SID:}")
    private String accountSid;

    public TwilioDiagnosticProbe(TwilioProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (accountSid == null || !accountSid.matches("^AC[0-9a-fA-F]{32}$") || !properties.hasAuthToken()) {
            log.warn("TWILIO_DIAG skipped: account SID or auth token is missing/invalid");
            return;
        }

        try {
            inspectRecentCalls();
            inspectRecentAlerts();
        } catch (Exception e) {
            log.warn("TWILIO_DIAG failed: {}", rootMessage(e));
        }
    }

    private void inspectRecentCalls() throws Exception {
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Calls.json?PageSize=10";
        JSONObject body = getJson(url);
        JSONArray calls = body.optJSONArray("calls");
        int count = calls == null ? 0 : calls.length();
        log.info("TWILIO_DIAG recent_calls={}", count);
        for (int i = 0; calls != null && i < calls.length(); i++) {
            JSONObject call = calls.getJSONObject(i);
            log.info("TWILIO_DIAG call sid={} status={} direction={} duration={} price={} created={}",
                    safe(call.optString("sid")),
                    safe(call.optString("status")),
                    safe(call.optString("direction")),
                    safe(call.optString("duration")),
                    safe(call.optString("price")),
                    safe(call.optString("date_created")));
        }
    }

    private void inspectRecentAlerts() throws Exception {
        String start = Instant.now().minus(12, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
        String url = "https://monitor.twilio.com/v1/Alerts?StartDate=" +
                URLEncoder.encode(start, StandardCharsets.UTF_8) + "&PageSize=50";
        JSONObject body = getJson(url);
        JSONArray alerts = body.optJSONArray("alerts");
        int count = alerts == null ? 0 : alerts.length();
        log.info("TWILIO_DIAG recent_alerts={}", count);
        for (int i = 0; alerts != null && i < alerts.length(); i++) {
            JSONObject alert = alerts.getJSONObject(i);
            log.info("TWILIO_DIAG alert sid={} code={} level={} resource={} generated={} text={}",
                    safe(alert.optString("sid")),
                    safe(alert.optString("error_code")),
                    safe(alert.optString("log_level")),
                    safe(alert.optString("resource_sid")),
                    safe(alert.optString("date_generated")),
                    sanitize(alert.optString("alert_text")));
        }
    }

    private JSONObject getJson(String url) throws Exception {
        String credentials = accountSid + ":" + properties.getAuthToken();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basic)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Twilio diagnostic GET failed status=" + response.statusCode());
        }
        return new JSONObject(response.body());
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return "";
        String out = ROUTE.matcher(value).replaceAll("$1[redacted]");
        out = PHONE.matcher(out).replaceAll("[phone]");
        out = out.replaceAll("[\\r\\n]+", " ").trim();
        return out.length() <= 350 ? out : out.substring(0, 350);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null) return "unknown";
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
