package cl.helvoca.messaging;

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
import java.util.regex.Pattern;

@Component
public class TwilioWhatsAppWebhookStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppWebhookStartupRunner.class);
    private static final String SENDERS_API = "https://messaging.twilio.com/v2/Channels/Senders";
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final boolean enabled;
    private final String senderE164;
    private final TwilioProperties twilio;
    private final HttpClient http;

    @Autowired
    public TwilioWhatsAppWebhookStartupRunner(
            @Value("${HELVOCA_WHATSAPP_WEBHOOK_CONFIGURE_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_SENDER_E164:}") String senderE164,
            TwilioProperties twilio) {
        this(enabled, senderE164, twilio,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    TwilioWhatsAppWebhookStartupRunner(boolean enabled,
                                       String senderE164,
                                       TwilioProperties twilio,
                                       HttpClient http) {
        this.enabled = enabled;
        this.senderE164 = senderE164 == null ? "" : senderE164.trim();
        this.twilio = twilio;
        this.http = http;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (!E164.matcher(senderE164).matches()) {
            throw new IllegalStateException("HELVOCA_WHATSAPP_SENDER_E164 must use E.164 format");
        }
        if (!twilio.hasAccountSid() || !twilio.hasAuthToken() || !twilio.hasSecurePublicBaseUrl()) {
            throw new IllegalStateException("Twilio credentials and public base URL are required");
        }

        try {
            String authorization = basicAuthorization();
            HttpRequest listRequest = HttpRequest.newBuilder(
                            URI.create(SENDERS_API + "?Channel=whatsapp&PageSize=100"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", authorization)
                    .GET()
                    .build();
            HttpResponse<String> listResponse = http.send(listRequest, HttpResponse.BodyHandlers.ofString());
            if (listResponse.statusCode() < 200 || listResponse.statusCode() >= 300) {
                throw new IllegalStateException("Twilio sender lookup failed with HTTP " + listResponse.statusCode());
            }

            JSONObject sender = findSender(new JSONObject(listResponse.body()).optJSONArray("senders"));
            String status = sender.optString("status", "");
            if (!"ONLINE".equalsIgnoreCase(status) && !"ONLINE:UPDATING".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Twilio WhatsApp sender is not online");
            }
            String sid = sender.optString("sid", "").trim();
            if (!sid.matches("^XE[0-9a-fA-F]{32}$")) {
                throw new IllegalStateException("Twilio WhatsApp sender SID is invalid");
            }

            JSONObject payload = new JSONObject()
                    .put("webhook", new JSONObject()
                            .put("callback_url", twilio.absoluteWebhook("/webhooks/v1/twilio/whatsapp"))
                            .put("callback_method", "POST"));

            HttpRequest updateRequest = HttpRequest.newBuilder(URI.create(SENDERS_API + "/" + sid))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<String> updateResponse = http.send(updateRequest, HttpResponse.BodyHandlers.ofString());
            if (updateResponse.statusCode() < 200 || updateResponse.statusCode() >= 300) {
                throw new IllegalStateException("Twilio sender webhook update failed with HTTP " + updateResponse.statusCode());
            }

            log.info("WHATSAPP_WEBHOOK_CONFIGURED senderEnding={}", lastFour(senderE164));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Twilio WhatsApp webhook configuration interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Twilio WhatsApp webhook configuration failed", e);
        }
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
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static String lastFour(String value) {
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }
}
