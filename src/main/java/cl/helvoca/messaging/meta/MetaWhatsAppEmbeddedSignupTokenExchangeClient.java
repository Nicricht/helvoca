package cl.helvoca.messaging.meta;

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

@Component
public class MetaWhatsAppEmbeddedSignupTokenExchangeClient {
    private final MetaWhatsAppProperties properties;
    private final HttpClient http;

    @Autowired
    public MetaWhatsAppEmbeddedSignupTokenExchangeClient(MetaWhatsAppProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build());
    }

    MetaWhatsAppEmbeddedSignupTokenExchangeClient(
            MetaWhatsAppProperties properties,
            HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public MetaWhatsAppEmbeddedSignupToken exchange(String authorizationCode) {
        String code = require(authorizationCode, "Meta authorization code is required");
        String appId = require(
                properties.getEmbeddedSignupAppId(),
                "Meta Embedded Signup app_id is required");
        String appSecret = require(
                properties.getAppSecret(),
                "Meta app secret is required");

        String body = form("client_id", appId)
                + "&" + form("client_secret", appSecret)
                + "&" + form("code", code);

        URI uri = URI.create(properties.graphApiRoot() + "/oauth/access_token");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Meta Embedded Signup token exchange failed with HTTP "
                                + response.statusCode());
            }

            JSONObject json = new JSONObject(response.body() == null ? "{}" : response.body());
            String accessToken = json.optString("access_token", "").trim();
            if (accessToken.isBlank()) {
                throw new IllegalStateException(
                        "Meta Embedded Signup token exchange returned no access token");
            }

            String tokenType = json.optString("token_type", "").trim();
            Long expiresIn = json.has("expires_in") && !json.isNull("expires_in")
                    ? json.optLong("expires_in")
                    : null;

            return new MetaWhatsAppEmbeddedSignupToken(
                    accessToken,
                    tokenType.isBlank() ? null : tokenType,
                    expiresIn);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Meta Embedded Signup token exchange was interrupted",
                    e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Meta Embedded Signup token exchange failed",
                    e);
        }
    }

    private static String form(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String require(String value, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return clean;
    }
}
