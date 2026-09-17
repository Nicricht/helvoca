package cl.helvoca.telephony.twilio;

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
import java.util.regex.Pattern;

@Component
public class TwilioDiagnosticStartupProbe implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioDiagnosticStartupProbe.class);
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final String API_BASE = "https://api.twilio.com";

    private final TwilioProperties properties;
    private final boolean enabled;
    private final HttpClient http;

    @Autowired
    public TwilioDiagnosticStartupProbe(
            TwilioProperties properties,
            @Value("${TWILIO_DIAGNOSTIC_PROBE:false}") boolean enabled) {
        this(properties, enabled, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build());
    }

    TwilioDiagnosticStartupProbe(TwilioProperties properties, boolean enabled, HttpClient http) {
        this.properties = properties;
        this.enabled = enabled;
        this.http = http;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        ProbeResult result = probe();
        if (result.success()) {
            log.info("TWILIO_DIAGNOSTIC_PROBE SUCCESS code={} detail={}", result.code(), result.detail());
        } else {
            log.error("TWILIO_DIAGNOSTIC_PROBE FAILED code={} detail={}", result.code(), result.detail());
        }
    }

    ProbeResult probe() {
        String accountSid = trim(properties.getAccountSid());
        String authToken = trim(properties.getAuthToken());

        if (accountSid.isEmpty() || authToken.isEmpty()) {
            return new ProbeResult(false, "NOT_CONFIGURED", "Twilio credentials are missing");
        }
        if (!ACCOUNT_SID.matcher(accountSid).matches()) {
            return new ProbeResult(false, "INVALID_ACCOUNT_SID", "Twilio account SID format is invalid");
        }

        String basic = Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        URI uri = URI.create(API_BASE + "/2010-04-01/Accounts/" + accountSid + ".json");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basic)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                return new ProbeResult(true, "OK", "Twilio account API accepted the configured credentials");
            }
            if (status >= 200 && status < 300) {
                return new ProbeResult(false, "UNEXPECTED_RESPONSE",
                        "Twilio account API returned unexpected HTTP " + status);
            }
            if (status == 401 || status == 403) {
                return new ProbeResult(false, "AUTH_FAILED", "Twilio rejected the configured credentials");
            }
            return new ProbeResult(false, "PROVIDER_ERROR", "Twilio account API returned HTTP " + status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProbeResult(false, "INTERRUPTED", "Twilio diagnostic was interrupted");
        } catch (Exception e) {
            return new ProbeResult(false, "NETWORK_ERROR", "Twilio account API could not be reached");
        }
    }

    record ProbeResult(boolean success, String code, String detail) {
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
