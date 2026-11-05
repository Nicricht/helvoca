package cl.helvoca.messaging.meta;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Verifies that the short-lived token returned by this customer's Embedded
 * Signup can read the exact WABA selected in the Meta session.
 */
@Component
public class MetaWhatsAppEmbeddedSignupWabaAccessClient {
    private final MetaWhatsAppProperties properties;
    private final HttpClient http;

    @Autowired
    public MetaWhatsAppEmbeddedSignupWabaAccessClient(MetaWhatsAppProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build());
    }

    MetaWhatsAppEmbeddedSignupWabaAccessClient(
            MetaWhatsAppProperties properties,
            HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public void requireAccess(String wabaId, String customerAccessToken) {
        if (wabaId == null || !wabaId.trim().matches("[0-9]{5,30}")) {
            throw new IllegalArgumentException("Invalid Meta WABA id");
        }
        if (customerAccessToken == null || customerAccessToken.isBlank()) {
            throw new IllegalStateException("Meta customer authorization token is unavailable");
        }

        URI uri = URI.create(properties.graphApiRoot() + "/"
                + wabaId.trim() + "?fields=id");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + customerAccessToken.trim())
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Meta customer WABA authorization read failed with HTTP "
                                + response.statusCode());
            }
            JSONObject body = new JSONObject(response.body() == null ? "{}" : response.body());
            if (!wabaId.trim().equals(body.optString("id", ""))) {
                throw new IllegalStateException("Meta customer WABA authorization readback mismatch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meta customer WABA authorization read was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Meta customer WABA authorization read failed", e);
        }
    }
}
