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
public class MetaWhatsAppEmbeddedSignupAssignedUsersClient {
    private final MetaWhatsAppProperties properties;
    private final HttpClient http;

    @Autowired
    public MetaWhatsAppEmbeddedSignupAssignedUsersClient(MetaWhatsAppProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build());
    }

    MetaWhatsAppEmbeddedSignupAssignedUsersClient(
            MetaWhatsAppProperties properties,
            HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public MetaWhatsAppEmbeddedSignupAssignedUsersResult fetch(
            String wabaId,
            String businessId,
            String systemUserAccessToken) {
        String cleanWabaId = requireNumericId(wabaId, "Meta WABA id is required", "Meta WABA id is invalid");
        String cleanBusinessId = requireNumericId(
                businessId,
                "Meta business id is required",
                "Meta business id is invalid");
        String bearer = require(
                systemUserAccessToken,
                "Meta system user access token is required");

        String encodedBusinessId = URLEncoder.encode(cleanBusinessId, StandardCharsets.UTF_8);
        URI uri = URI.create(
                properties.graphApiRoot()
                        + "/"
                        + cleanWabaId
                        + "/assigned_users?business="
                        + encodedBusinessId);

        HttpRequest request = HttpRequest.newBuilder(uri)
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
                        "Meta Embedded Signup assigned users lookup failed with HTTP "
                                + response.statusCode());
            }

            JSONObject json = new JSONObject(response.body() == null ? "{}" : response.body());
            JSONArray data = json.optJSONArray("data");
            if (data == null) {
                throw new IllegalStateException(
                        "Meta Embedded Signup assigned users lookup returned no data");
            }

            List<MetaWhatsAppEmbeddedSignupAssignedUsersResult.AssignedUser> users =
                    new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;

                String id = clean(item.optString("id", ""));
                if (id == null) {
                    throw new IllegalStateException(
                            "Meta Embedded Signup assigned users lookup returned user without id");
                }

                users.add(new MetaWhatsAppEmbeddedSignupAssignedUsersResult.AssignedUser(
                        id,
                        clean(item.optString("name", "")),
                        stringArray(item.optJSONArray("tasks"))));
            }

            return new MetaWhatsAppEmbeddedSignupAssignedUsersResult(users);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Meta Embedded Signup assigned users lookup was interrupted",
                    e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Meta Embedded Signup assigned users lookup failed",
                    e);
        }
    }

    private static List<String> stringArray(JSONArray values) {
        if (values == null) return List.of();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            String value = clean(values.optString(i, ""));
            if (value != null) result.add(value);
        }
        return List.copyOf(result);
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
        String clean = clean(value);
        if (clean == null) throw new IllegalArgumentException(message);
        return clean;
    }

    private static String clean(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isBlank() ? null : clean;
    }
}
