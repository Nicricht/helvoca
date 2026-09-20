package cl.helvoca.messaging.meta;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class MetaWhatsAppEmbeddedSignupRegisterPhoneClient {
    private final MetaWhatsAppProperties properties;
    private final HttpClient http;

    @Autowired
    public MetaWhatsAppEmbeddedSignupRegisterPhoneClient(
            MetaWhatsAppProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build());
    }

    MetaWhatsAppEmbeddedSignupRegisterPhoneClient(
            MetaWhatsAppProperties properties,
            HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public MetaWhatsAppEmbeddedSignupRegisterPhoneResult register(
            String phoneNumberId,
            String systemUserAccessToken,
            String pin) {
        String cleanPhoneNumberId = requireNumericId(
                phoneNumberId,
                "Meta phone number id is required",
                "Meta phone number id is invalid");
        String bearer = require(
                systemUserAccessToken,
                "Meta phone registration access token is required");
        String cleanPin = requirePin(pin);

        URI uri = URI.create(
                properties.graphApiRoot()
                        + "/"
                        + cleanPhoneNumberId
                        + "/register");

        JSONObject payload = new JSONObject()
                .put("messaging_product", "whatsapp")
                .put("pin", cleanPin);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        try {
            HttpResponse<String> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Meta Embedded Signup phone registration failed with HTTP "
                                + response.statusCode());
            }

            JSONObject json = new JSONObject(response.body() == null ? "{}" : response.body());
            if (!json.has("success")) {
                throw new IllegalStateException(
                        "Meta Embedded Signup phone registration returned no success flag");
            }

            return new MetaWhatsAppEmbeddedSignupRegisterPhoneResult(
                    json.optBoolean("success", false));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Meta Embedded Signup phone registration was interrupted",
                    e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Meta Embedded Signup phone registration failed",
                    e);
        }
    }

    private static String requireNumericId(
            String value,
            String missingMessage,
            String invalidMessage) {
        String clean = require(value, missingMessage);
        if (!clean.matches("[0-9]{1,80}")) {
            throw new IllegalArgumentException(invalidMessage);
        }
        return clean;
    }

    private static String requirePin(String pin) {
        String clean = require(pin, "Meta phone registration PIN is required");
        if (!clean.matches("[0-9]{6}")) {
            throw new IllegalArgumentException(
                    "Meta phone registration PIN must contain exactly 6 digits");
        }
        return clean;
    }

    private static String require(String value, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return clean;
    }
}
