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
 * Read-only provider proof: a coexistence number must still be on the
 * WhatsApp Business App AND registered on Cloud API.
 */
@Component
public class MetaWhatsAppCoexistencePhoneStatusClient {
    private final MetaWhatsAppProperties properties;
    private final HttpClient http;

    @Autowired
    public MetaWhatsAppCoexistencePhoneStatusClient(MetaWhatsAppProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build());
    }

    MetaWhatsAppCoexistencePhoneStatusClient(
            MetaWhatsAppProperties properties, HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    /**
     * Read-only guard for the ordinary registration path. If Meta cannot
     * explicitly report the Business App flag, callers must fail closed.
     */
    public boolean isOnBusinessApp(String phoneNumberId, String accessToken) {
        if (phoneNumberId == null || !phoneNumberId.matches("[0-9]{5,30}")) {
            throw new IllegalArgumentException("Invalid Meta phone number id");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Meta access token unavailable");
        }
        URI uri = URI.create(properties.graphApiRoot() + "/" + phoneNumberId
                + "?fields=is_on_biz_app");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + accessToken.trim())
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Meta Business App status HTTP "
                        + response.statusCode());
            }
            JSONObject json = new JSONObject(response.body() == null ? "{}" : response.body());
            if (!phoneNumberId.equals(json.optString("id", "")) || !json.has("is_on_biz_app")) {
                throw new IllegalStateException("Meta Business App status readback incomplete");
            }
            return json.optBoolean("is_on_biz_app", false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meta Business App status readback interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Meta Business App status readback failed", e);
        }
    }

    public Status read(String phoneNumberId, String accessToken) {
        if (phoneNumberId == null || !phoneNumberId.matches("[0-9]{5,30}")) {
            throw new IllegalArgumentException("Invalid Meta phone number id");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Meta coexistence access token unavailable");
        }
        URI uri = URI.create(properties.graphApiRoot() + "/" + phoneNumberId
                + "?fields=display_phone_number,is_on_biz_app,platform_type");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + accessToken.trim())
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Meta coexistence readback HTTP "
                        + response.statusCode());
            }
            JSONObject json = new JSONObject(response.body() == null ? "{}" : response.body());
            if (!phoneNumberId.equals(json.optString("id", ""))
                    || !json.has("is_on_biz_app")
                    || !json.has("platform_type")
                    || !json.has("display_phone_number")) {
                throw new IllegalStateException("Meta coexistence status incomplete");
            }
            return new Status(
                    json.optBoolean("is_on_biz_app", false),
                    json.optString("platform_type", ""),
                    json.optString("display_phone_number", ""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meta coexistence readback interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Meta coexistence readback failed", e);
        }
    }

    public record Status(boolean isOnBusinessApp, String platformType, String displayPhoneNumber) {
        public boolean verified() {
            return isOnBusinessApp && "CLOUD_API".equals(platformType)
                    && displayPhoneNumber != null && !displayPhoneNumber.isBlank();
        }
    }
}
