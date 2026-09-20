package cl.helvoca.messaging.meta;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class MetaWhatsAppEmbeddedSignupSharedWabaClient {
    private final MetaWhatsAppProperties properties;
    private final HttpClient http;

    @Autowired
    public MetaWhatsAppEmbeddedSignupSharedWabaClient(MetaWhatsAppProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build());
    }

    MetaWhatsAppEmbeddedSignupSharedWabaClient(
            MetaWhatsAppProperties properties,
            HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public MetaWhatsAppEmbeddedSignupSharedWabaPage list(
            String businessId,
            String systemUserAccessToken) {
        return list(businessId, systemUserAccessToken, null);
    }

    public MetaWhatsAppEmbeddedSignupSharedWabaPage list(
            String businessId,
            String systemUserAccessToken,
            String afterCursor) {
        String cleanBusinessId = requireBusinessId(businessId);
        String bearer = require(
                systemUserAccessToken,
                "Meta system user access token is required");

        StringBuilder url = new StringBuilder(properties.graphApiRoot())
                .append("/")
                .append(cleanBusinessId)
                .append("/client_whatsapp_business_accounts");

        String cleanCursor = clean(afterCursor);
        if (cleanCursor != null) {
            url.append("?after=")
                    .append(URLEncoder.encode(cleanCursor, StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Meta Embedded Signup shared WABA lookup failed with HTTP "
                                + response.statusCode());
            }

            JSONObject json = new JSONObject(response.body() == null ? "{}" : response.body());
            JSONArray data = json.optJSONArray("data");
            if (data == null) {
                throw new IllegalStateException(
                        "Meta Embedded Signup shared WABA lookup returned no data");
            }

            List<MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba> wabas = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;

                String id = clean(item.optString("id", ""));
                if (id == null) {
                    throw new IllegalStateException(
                            "Meta Embedded Signup shared WABA lookup returned WABA without id");
                }

                wabas.add(new MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba(
                        id,
                        clean(item.optString("name", "")),
                        clean(item.optString("currency", "")),
                        clean(item.optString("timezone_id", "")),
                        clean(item.optString("message_template_namespace", ""))));
            }

            return new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                    wabas,
                    afterCursor(json));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Meta Embedded Signup shared WABA lookup was interrupted",
                    e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Meta Embedded Signup shared WABA lookup failed",
                    e);
        }
    }

    private static String afterCursor(JSONObject json) {
        JSONObject paging = json.optJSONObject("paging");
        if (paging == null) return null;
        JSONObject cursors = paging.optJSONObject("cursors");
        if (cursors == null) return null;
        return clean(cursors.optString("after", ""));
    }

    private static String requireBusinessId(String businessId) {
        String clean = require(businessId, "Meta business id is required");
        if (!clean.matches("[0-9]{1,80}")) {
            throw new IllegalArgumentException("Meta business id is invalid");
        }
        return clean;
    }

    private static String require(String value, String message) {
        String clean = clean(value);
        if (clean == null) throw new IllegalArgumentException(message);
        return clean;
    }

    private static String clean(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isBlank() ? null : clean;
    }
}
