package cl.helvoca.telephony.twilio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Temporary, read-only credential probe. It performs exactly one GET against
 * Twilio's Calls collection with PageSize=1. It never creates, updates or
 * deletes calls and never logs credentials or response bodies.
 */
@Component
public class TwilioCredentialProbe implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioCredentialProbe.class);

    private final TwilioProperties properties;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${TWILIO_CREDENTIAL_PROBE:false}")
    private boolean enabled;

    @Value("${TWILIO_ACCOUNT_SID:}")
    private String accountSid;

    public TwilioCredentialProbe(TwilioProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (accountSid == null || !accountSid.matches("^AC[0-9a-fA-F]{32}$") || !properties.hasAuthToken()) {
            log.warn("TWILIO_CREDENTIAL_CHECK result=CONFIG_INVALID");
            return;
        }

        try {
            String basic = Base64.getEncoder().encodeToString(
                    (accountSid + ":" + properties.getAuthToken()).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Calls.json?PageSize=1"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + basic)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("TWILIO_CREDENTIAL_CHECK result=OK status={}", response.statusCode());
            } else {
                log.warn("TWILIO_CREDENTIAL_CHECK result=FAILED status={}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("TWILIO_CREDENTIAL_CHECK result=ERROR type={}", e.getClass().getSimpleName());
        }
    }
}
