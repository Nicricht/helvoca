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
import java.util.Set;

@Component
public class MetaWhatsAppEmbeddedSignupAssignSystemUserClient {
    private static final Set<String> ALLOWED_TASKS = Set.of("MANAGE", "DEVELOP");

    private final MetaWhatsAppProperties properties;
    private final HttpClient http;

    @Autowired
    public MetaWhatsAppEmbeddedSignupAssignSystemUserClient(
            MetaWhatsAppProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build());
    }

    MetaWhatsAppEmbeddedSignupAssignSystemUserClient(
            MetaWhatsAppProperties properties,
            HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public MetaWhatsAppEmbeddedSignupAssignSystemUserResult assign(
            String wabaId,
            String systemUserId,
            String task,
            String adminSystemUserAccessToken) {
        String cleanWabaId = requireNumericId(
                wabaId,
                "Meta WABA id is required",
                "Meta WABA id is invalid");
        String cleanSystemUserId = requireNumericId(
                systemUserId,
                "Meta system user id is required",
                "Meta system user id is invalid");
        String cleanTask = requireTask(task);
        String bearer = require(
                adminSystemUserAccessToken,
                "Meta admin system user access token is required");

        String encodedUserId = URLEncoder.encode(cleanSystemUserId, StandardCharsets.UTF_8);
        String encodedTasks = URLEncoder.encode(
                "['" + cleanTask + "']",
                StandardCharsets.UTF_8);

        URI uri = URI.create(
                properties.graphApiRoot()
                        + "/"
                        + cleanWabaId
                        + "/assigned_users?user="
                        + encodedUserId
                        + "&tasks="
                        + encodedTasks);

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
                        "Meta Embedded Signup system user assignment failed with HTTP "
                                + response.statusCode());
            }

            JSONObject json = new JSONObject(response.body() == null ? "{}" : response.body());
            if (!json.has("success")) {
                throw new IllegalStateException(
                        "Meta Embedded Signup system user assignment returned no success flag");
            }

            return new MetaWhatsAppEmbeddedSignupAssignSystemUserResult(
                    json.optBoolean("success", false));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Meta Embedded Signup system user assignment was interrupted",
                    e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Meta Embedded Signup system user assignment failed",
                    e);
        }
    }

    private static String requireTask(String task) {
        String clean = require(task, "Meta system user task is required").toUpperCase();
        if (!ALLOWED_TASKS.contains(clean)) {
            throw new IllegalArgumentException("Meta system user task is invalid");
        }
        return clean;
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
