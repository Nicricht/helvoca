package cl.helvoca.telephony.twilio;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * One-shot, read-only diagnostic for an already-created Twilio Call SID.
 * It performs a single authenticated GET and can never create, update or hang up a call.
 * Keep both explicit opt-in settings empty/off outside the diagnostic window.
 */
@Component
public final class TwilioCertificationCallStatusReadOnlyRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioCertificationCallStatusReadOnlyRunner.class);
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final Pattern CALL_SID = Pattern.compile("^CA[0-9a-fA-F]{32}$");
    private static final AtomicBoolean FIRED = new AtomicBoolean(false);

    private final boolean enabled;
    private final String callSid;
    private final TwilioProperties properties;
    private final HttpClient http;

    @Autowired
    public TwilioCertificationCallStatusReadOnlyRunner(
            @Value("${TWILIO_CERTIFICATION_CALL_STATUS_LOOKUP_ENABLED:false}") boolean enabled,
            @Value("${TWILIO_CERTIFICATION_CALL_STATUS_LOOKUP_SID:}") String callSid,
            TwilioProperties properties) {
        this(enabled, callSid, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    TwilioCertificationCallStatusReadOnlyRunner(
            boolean enabled, String callSid, TwilioProperties properties, HttpClient http) {
        this.enabled = enabled;
        this.callSid = callSid;
        this.properties = properties;
        this.http = http;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || callSid == null || callSid.isBlank() || !FIRED.compareAndSet(false, true)) return;
        try {
            CallSnapshot result = lookup(callSid);
            log.info(
                    "TWILIO_CALL_STATUS_READ_ONLY call={} status={} duration={} price={} price_unit={} error_code={} direction={} answered_by={} from={} to={} start_time={} end_time={} date_updated={}",
                    callSid, result.status(), result.duration(), result.price(), result.priceUnit(),
                    result.errorCode(), result.direction(), result.answeredBy(), result.fromSuffix(),
                    result.toSuffix(), result.startTime(), result.endTime(), result.dateUpdated());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("TWILIO_CALL_STATUS_READ_ONLY FAILED call={} reason=interrupted", callSid);
        } catch (Exception e) {
            log.error("TWILIO_CALL_STATUS_READ_ONLY FAILED call={} reason={}", callSid,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    CallSnapshot lookup(String requestedCallSid) throws Exception {
        if (requestedCallSid == null || !CALL_SID.matcher(requestedCallSid.trim()).matches()) {
            throw new IllegalArgumentException("invalid Call SID");
        }
        if (!properties.hasAccountSid() || !ACCOUNT_SID.matcher(properties.getAccountSid().trim()).matches()
                || !properties.hasAuthToken()) {
            throw new IllegalStateException("Twilio read-only credentials unavailable");
        }

        String accountSid = properties.getAccountSid().trim();
        String credentials = accountSid + ":" + properties.getAuthToken().trim();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        URI uri = URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid
                + "/Calls/" + requestedCallSid.trim() + ".json");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basic)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Twilio read-only GET http=" + response.statusCode());
        }

        JSONObject json = new JSONObject(response.body());
        if (!requestedCallSid.trim().equals(json.optString("sid", ""))
                || !accountSid.equals(json.optString("account_sid", ""))) {
            throw new IllegalStateException("Twilio response identity mismatch");
        }

        return new CallSnapshot(
                json.optString("status", ""),
                json.optString("duration", ""),
                json.optString("price", ""),
                json.optString("price_unit", ""),
                json.isNull("error_code") ? "" : json.optString("error_code", ""),
                json.optString("direction", ""),
                json.optString("answered_by", ""),
                suffix(json.optString("from", "")),
                suffix(json.optString("to", "")),
                json.optString("start_time", ""),
                json.optString("end_time", ""),
                json.optString("date_updated", ""));
    }

    private static String suffix(String value) {
        if (value == null || value.isBlank()) return "";
        String digits = value.replaceAll("\\D", "");
        return digits.length() < 4 ? "****" : "…" + digits.substring(digits.length() - 4);
    }

    record CallSnapshot(
            String status,
            String duration,
            String price,
            String priceUnit,
            String errorCode,
            String direction,
            String answeredBy,
            String fromSuffix,
            String toSuffix,
            String startTime,
            String endTime,
            String dateUpdated) {}
}
