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
    private static final Pattern MEDIA_ID = Pattern.compile("^[0-9]{5,40}$");
    private static final int MAX_MEDIA_BYTES = 25 * 1024 * 1024;

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

    public void subscribeWaba(String wabaId, String accessToken) {
        String accountId = normalizeWabaId(wabaId);
        String token = require(accessToken, "Meta WhatsApp access token is required");

        URI uri = URI.create(properties.graphApiRoot() + "/" + accountId + "/subscribed_apps");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw apiError(response.statusCode(), response.body());
            }
            JSONObject body = new JSONObject(response.body());
            if (!body.optBoolean("success", false)) {
                throw new IllegalStateException("Meta WhatsApp WABA subscription was not confirmed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meta WhatsApp WABA subscription was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Meta WhatsApp WABA subscription failed", e);
        }
    }


    public AccessDiagnostic diagnoseAccess(String wabaId, String accessToken) {
        String accountId = normalizeWabaId(wabaId);
        String token = require(accessToken, "Meta WhatsApp access token is required");

        String managementPermission = "MISSING";
        String messagingPermission = "MISSING";
        String permissionsFailure = "NONE";

        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(properties.graphApiRoot() + "/me/permissions"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                MetaWhatsAppApiException error = apiError(response.statusCode(), response.body());
                permissionsFailure = error.failureCode();
                managementPermission = "UNKNOWN";
                messagingPermission = "UNKNOWN";
            } else {
                JSONArray data = new JSONObject(response.body()).optJSONArray("data");
                managementPermission = permissionStatus(data, "whatsapp_business_management");
                messagingPermission = permissionStatus(data, "whatsapp_business_messaging");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            permissionsFailure = "INTERRUPTED";
            managementPermission = "UNKNOWN";
            messagingPermission = "UNKNOWN";
        } catch (Exception e) {
            permissionsFailure = "LOCAL_ERROR";
            managementPermission = "UNKNOWN";
            messagingPermission = "UNKNOWN";
        }

        boolean wabaReadable = false;
        String wabaReadFailure = "NONE";
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(properties.graphApiRoot() + "/" + accountId + "?fields=id"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                wabaReadFailure = apiError(response.statusCode(), response.body()).failureCode();
            } else {
                String returnedId = new JSONObject(response.body()).optString("id", "").trim();
                wabaReadable = accountId.equals(returnedId);
                if (!wabaReadable) wabaReadFailure = "ID_MISMATCH";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            wabaReadFailure = "INTERRUPTED";
        } catch (Exception e) {
            wabaReadFailure = "LOCAL_ERROR";
        }

        return new AccessDiagnostic(
                managementPermission,
                messagingPermission,
                permissionsFailure,
                wabaReadable,
                wabaReadFailure);
    }

    public record AccessDiagnostic(
            String managementPermission,
            String messagingPermission,
            String permissionsFailure,
            boolean wabaReadable,
            String wabaReadFailure) {}

    public DownloadedMedia downloadMedia(String mediaId, String accessToken) {
        String id = normalizeMediaId(mediaId);
        String token = require(accessToken, "Meta WhatsApp access token is required");

        try {
            HttpRequest metadataRequest = HttpRequest.newBuilder(
                            URI.create(properties.graphApiRoot() + "/" + id))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> metadataResponse = http.send(
                    metadataRequest, HttpResponse.BodyHandlers.ofString());
            if (metadataResponse.statusCode() < 200 || metadataResponse.statusCode() >= 300) {
                throw apiError(metadataResponse.statusCode(), metadataResponse.body());
            }

            JSONObject metadata = new JSONObject(metadataResponse.body());
            String url = require(metadata.optString("url", null), "Meta WhatsApp media URL is missing");
            long declaredSize = metadata.optLong("file_size", -1L);
            if (declaredSize > MAX_MEDIA_BYTES) {
                throw new IllegalStateException("Meta WhatsApp audio exceeds the supported size");
            }
            URI mediaUri = URI.create(url);
            if (!"https".equalsIgnoreCase(mediaUri.getScheme())) {
                throw new IllegalStateException("Meta WhatsApp media URL must use HTTPS");
            }

            HttpRequest mediaRequest = HttpRequest.newBuilder(mediaUri)
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<byte[]> mediaResponse = http.send(
                    mediaRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (mediaResponse.statusCode() < 200 || mediaResponse.statusCode() >= 300) {
                throw new IllegalStateException("Meta WhatsApp media download failed");
            }
            byte[] bytes = mediaResponse.body() == null ? new byte[0] : mediaResponse.body();
            if (bytes.length == 0) {
                throw new IllegalStateException("Meta WhatsApp audio is empty");
            }
            if (bytes.length > MAX_MEDIA_BYTES) {
                throw new IllegalStateException("Meta WhatsApp audio exceeds the supported size");
            }
            String contentType = metadata.optString("mime_type", "");
            if (contentType.isBlank()) {
                contentType = mediaResponse.headers().firstValue("Content-Type").orElse("audio/ogg");
            }
            return new DownloadedMedia(bytes, contentType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meta WhatsApp media download was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Meta WhatsApp media download failed", e);
        }
    }

    public record DownloadedMedia(byte[] bytes, String contentType) {}

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
                throw apiError(response.statusCode(), response.body());
            }

            JSONObject body = new JSONObject(response.body());
            JSONArray messages = body.optJSONArray("messages");
            if (messages == null || messages.length() == 0) {
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


    private static String permissionStatus(JSONArray data, String permissionName) {
        if (data == null) return "MISSING";
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            if (!permissionName.equals(item.optString("permission", ""))) continue;
            String status = item.optString("status", "UNKNOWN").trim();
            return status.isBlank() ? "UNKNOWN" : status.toUpperCase();
        }
        return "MISSING";
    }

    private static MetaWhatsAppApiException apiError(int httpStatus, String rawBody) {
        String code = null;
        String subcode = null;
        String type = null;
        String trace = null;
        boolean transientFailure = false;
        try {
            JSONObject error = new JSONObject(rawBody == null ? "{}" : rawBody)
                    .optJSONObject("error");
            if (error != null) {
                code = scalar(error, "code");
                subcode = scalar(error, "error_subcode");
                type = scalar(error, "type");
                trace = scalar(error, "fbtrace_id");
                transientFailure = error.optBoolean("is_transient", false);
            }
        } catch (RuntimeException ignored) {
            // Diagnostics remain fail-closed: never surface the raw provider body.
        }
        return new MetaWhatsAppApiException(
                httpStatus, code, subcode, transientFailure, type, trace);
    }

    private static String scalar(JSONObject source, String key) {
        if (source == null || !source.has(key) || source.isNull(key)) return null;
        String value = String.valueOf(source.get(key)).trim();
        return value.isBlank() ? null : value;
    }

    private static String normalizeMediaId(String value) {
        String clean = require(value, "Meta media id is required");
        if (!MEDIA_ID.matcher(clean).matches()) {
            throw new IllegalArgumentException("Invalid Meta media id");
        }
        return clean;
    }

    private static String normalizeWabaId(String value) {
        String clean = require(value, "Meta WABA id is required");
        if (!PHONE_NUMBER_ID.matcher(clean).matches()) {
            throw new IllegalArgumentException("Invalid Meta WABA id");
        }
        return clean;
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
