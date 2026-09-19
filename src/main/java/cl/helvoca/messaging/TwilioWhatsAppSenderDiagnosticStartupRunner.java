package cl.helvoca.messaging;

import cl.helvoca.telephony.twilio.TwilioProperties;
import org.json.JSONArray;
import org.json.JSONObject;
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
import java.util.regex.Pattern;

@Component
public class TwilioWhatsAppSenderDiagnosticStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppSenderDiagnosticStartupRunner.class);
    private static final String SENDERS_API = "https://messaging.twilio.com/v2/Channels/Senders";
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final boolean enabled;
    private final String senderE164;
    private final TwilioProperties twilio;
    private final HttpClient http;

    public TwilioWhatsAppSenderDiagnosticStartupRunner(
            @Value("${HELVOCA_WHATSAPP_DIAGNOSTIC_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_SENDER_E164:}") String senderE164,
            TwilioProperties twilio) {
        this.enabled = enabled;
        this.senderE164 = senderE164 == null ? "" : senderE164.trim();
        this.twilio = twilio;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;
        if (!E164.matcher(senderE164).matches()) {
            throw new IllegalStateException("HELVOCA_WHATSAPP_SENDER_E164 must use E.164 format");
        }
        if (!twilio.hasAccountSid() || !twilio.hasAuthToken()) {
            throw new IllegalStateException("Twilio credentials are required");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(SENDERS_API + "?Channel=whatsapp&PageSize=100"))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", basicAuthorization())
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Twilio sender diagnostic failed with HTTP " + response.statusCode());
        }

        JSONObject sender = findSender(new JSONObject(response.body()).optJSONArray("senders"));
        JSONObject configuration = sender.optJSONObject("configuration");
        JSONArray offlineReasons = sender.optJSONArray("offline_reasons");

        StringBuilder reasons = new StringBuilder();
        if (offlineReasons != null) {
            for (int i = 0; i < offlineReasons.length(); i++) {
                JSONObject reason = offlineReasons.optJSONObject(i);
                if (reason == null) continue;
                if (!reasons.isEmpty()) reasons.append(" | ");
                reasons.append("code=").append(sanitize(reason.optString("code", "")))
                        .append(",message=").append(sanitize(reason.optString("message", "")));
            }
        }

        log.info(
                "WHATSAPP_SENDER_DIAGNOSTIC ending={} status={} verificationMethod={} wabaPresent={} offlineReasons={}",
                lastFour(senderE164),
                sanitize(sender.optString("status", "")),
                configuration == null ? "" : sanitize(configuration.optString("verification_method", "")),
                configuration != null && !configuration.optString("waba_id", "").isBlank(),
                reasons
        );
    }

    private JSONObject findSender(JSONArray senders) {
        if (senders == null) throw new IllegalStateException("Twilio returned no WhatsApp senders");
        String expected = "whatsapp:" + senderE164;
        for (int i = 0; i < senders.length(); i++) {
            JSONObject sender = senders.optJSONObject(i);
            if (sender != null && expected.equalsIgnoreCase(sender.optString("sender_id", ""))) {
                return sender;
            }
        }
        throw new IllegalStateException("Configured WhatsApp sender was not found in Twilio");
    }

    private String basicAuthorization() {
        String credentials = twilio.getAccountSid().trim() + ":" + twilio.getAuthToken().trim();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim()
                .replaceAll("\\+[1-9][0-9]{7,14}", "[redacted-phone]");
        return clean.length() <= 400 ? clean : clean.substring(0, 400);
    }

    private static String lastFour(String value) {
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }
}
