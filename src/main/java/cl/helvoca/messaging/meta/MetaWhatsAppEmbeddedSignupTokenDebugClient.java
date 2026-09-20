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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class MetaWhatsAppEmbeddedSignupTokenDebugClient {
    private final MetaWhatsAppProperties properties;
    private final HttpClient http;

    @Autowired
    public MetaWhatsAppEmbeddedSignupTokenDebugClient(MetaWhatsAppProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build());
    }

    MetaWhatsAppEmbeddedSignupTokenDebugClient(
            MetaWhatsAppProperties properties,
            HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public MetaWhatsAppEmbeddedSignupTokenDebugResult debug(
            String userAccessToken,
            String systemUserAccessToken) {
        String inputToken = require(userAccessToken, "Meta user access token is required");
        String debuggerToken = require(
                systemUserAccessToken,
                "Meta system user access token is required");

        String encoded = URLEncoder.encode(inputToken, StandardCharsets.UTF_8);
        URI uri = URI.create(properties.graphApiRoot() + "/debug_token?input_token=" + encoded);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + debuggerToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Meta Embedded Signup token debug failed with HTTP "
                                + response.statusCode());
            }

            JSONObject data = new JSONObject(response.body() == null ? "{}" : response.body())
                    .optJSONObject("data");
            if (data == null) {
                throw new IllegalStateException(
                        "Meta Embedded Signup token debug returned no data");
            }

            return new MetaWhatsAppEmbeddedSignupTokenDebugResult(
                    data.optBoolean("is_valid", false),
                    clean(data.optString("app_id", "")),
                    clean(data.optString("type", "")),
                    nullableLong(data, "expires_at"),
                    nullableLong(data, "data_access_expires_at"),
                    stringArray(data.optJSONArray("scopes")),
                    granularTargetIds(data.optJSONArray("granular_scopes")));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Meta Embedded Signup token debug was interrupted",
                    e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Meta Embedded Signup token debug failed",
                    e);
        }
    }

    private static List<String> granularTargetIds(JSONArray granularScopes) {
        if (granularScopes == null) return List.of();
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < granularScopes.length(); i++) {
            JSONObject entry = granularScopes.optJSONObject(i);
            if (entry == null) continue;
            JSONArray targets = entry.optJSONArray("target_ids");
            if (targets == null) continue;
            ids.addAll(stringArray(targets));
        }
        return List.copyOf(ids);
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

    private static Long nullableLong(JSONObject source, String key) {
        if (!source.has(key) || source.isNull(key)) return null;
        return source.optLong(key);
    }

    private static String clean(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isBlank() ? null : clean;
    }

    private static String require(String value, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) throw new IllegalArgumentException(message);
        return clean;
    }
}
