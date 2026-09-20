package cl.helvoca.messaging.meta;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

@Component
public class MetaWhatsAppCloudClient {
    private static final Pattern PHONE_NUMBER_ID = Pattern.compile("^[0-9]{5,30}$");
    private static final Pattern RECIPIENT = Pattern.compile("^[1-9][0-9]{7,14}$");

    private final MetaWhatsAppProperties properties;
    private final HttpClient http;

    @Autowired
    public MetaWhatsAppCloudClient(MetaWhatsAppProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    MetaWhatsAppCloudClient(MetaWhatsAppProperties properties, HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public String sendText(String phoneNumberId,
                           String accessToken,
                           String recipient,
                           String content) {
        String senderId = normalizePhoneNumberId(phoneNumberId);
        String token = require(accessToken, "Meta WhatsApp access token is required");
        String to = normalizeRecipient(recipient);
        String text = require(content, "Outbound content is required");

        JSONObject payload = new JSONObject()
                .put("messaging_product", "whatsapp")
                .put("recipient_type", "individual")
                .put("to", to)
                .put("type", "text")
                .put("text", new JSONObject()
                        .put("preview_url", false)
                        .put("body", text));

        URI uri = URI.create(properties.graphApiRoot() + "/" + senderId + "/messages");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Meta WhatsApp rejected message with HTTP " + response.statusCode());
            }

            JSONObject body = new JSONObject(response.body());
            JSONArray messages = body.optJSONArray("messages");
            if (messages == null || messages.isEmpty()) {
                throw new IllegalStateException("Meta WhatsApp did not return a message id");
            }
            String messageId = messages.optJSONObject(0) == null
                    ? ""
                    : messages.optJSONObject(0).optString("id", "").trim();
            if (messageId.isBlank()) {
                throw new IllegalStateException("Meta WhatsApp did not return a message id");
            }
            return messageId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meta WhatsApp send was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Meta WhatsApp send failed", e);
        }
    }

    private static String normalizePhoneNumberId(String value) {
        String clean = require(value, "Meta phone_number_id is required");
        if (!PHONE_NUMBER_ID.matcher(clean).matches()) {
            throw new IllegalArgumentException("Invalid Meta phone_number_id");
        }
        return clean;
    }

    private static String normalizeRecipient(String value) {
        String clean = require(value, "WhatsApp recipient is required");
        if (clean.regionMatches(true, 0, "whatsapp:", 0, 9)) {
            clean = clean.substring(9).trim();
        }
        if (clean.startsWith("+")) {
            clean = clean.substring(1);
        }
        if (!RECIPIENT.matcher(clean).matches()) {
            throw new IllegalArgumentException("WhatsApp recipient must be a valid international number");
        }
        return clean;
    }

    private static String require(String value, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) throw new IllegalArgumentException(message);
        return clean;
    }
}
