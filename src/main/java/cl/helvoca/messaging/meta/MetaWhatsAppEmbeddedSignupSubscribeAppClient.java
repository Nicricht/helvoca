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
public class MetaWhatsAppEmbeddedSignupSubscribeAppClient {
    private final MetaWhatsAppProperties properties;
    private final HttpClient http;

    @Autowired
    public MetaWhatsAppEmbeddedSignupSubscribeAppClient(
            MetaWhatsAppProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build());
    }

    MetaWhatsAppEmbeddedSignupSubscribeAppClient(
            MetaWhatsAppProperties properties,
            HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public MetaWhatsAppEmbeddedSignupSubscribeAppResult subscribe(
            String wabaId,
            String accessToken) {
        String cleanWabaId = requireNumericId(
                wabaId,
                "Meta WABA id is required",
                "Meta WABA id is invalid");
        String bearer = require(
                accessToken,
                "Meta WABA subscription access token is required");

        URI uri = URI.create(
                properties.graphApiRoot()
                        + "/"
                        + cleanWabaId
                        + "/subscribed_apps");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<String> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Meta Embedded Signup app subscription failed with HTTP "
                                + response.statusCode());
            }

            JSONObject json = new JSONObject(response.body() == null ? "{}" : response.body());
            if (!json.has("success")) {
                throw new IllegalStateException(
                        "Meta Embedded Signup app subscription returned no success flag");
            }

            return new MetaWhatsAppEmbeddedSignupSubscribeAppResult(
                    json.optBoolean("success", false));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Meta Embedded Signup app subscription was interrupted",
                    e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Meta Embedded Signup app subscription failed",
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

    private static String require(String value, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return clean;
    }
}
