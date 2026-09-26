package cl.helvoca.payment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

@Component
public class MercadoPagoOrderClient {
    private static final String BASE_URL = "https://api.mercadopago.com/v1/orders";
    private final HttpClient http;

    public MercadoPagoOrderClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    MercadoPagoOrderClient(HttpClient http) {
        this.http = http;
    }

    public RemoteOrder create(String accessToken,
                              String idempotencyKey,
                              UUID paymentOperationId,
                              BigDecimal amount) {
        JSONObject payload = new JSONObject()
                .put("type", "online")
                .put("processing_mode", "manual")
                .put("total_amount", amount.toPlainString())
                .put("external_reference", paymentOperationId.toString());
        return send("POST", BASE_URL, accessToken, idempotencyKey, payload.toString());
    }

    public RemoteOrder get(String accessToken, String externalId) {
        return send("GET", BASE_URL + "/" + path(externalId), accessToken, null, null);
    }

    public RemoteOrder cancel(String accessToken, String externalId, String idempotencyKey) {
        return send("POST", BASE_URL + "/" + path(externalId) + "/cancel",
                accessToken, idempotencyKey, null);
    }

    private RemoteOrder send(String method,
                             String url,
                             String accessToken,
                             String idempotencyKey,
                             String body) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Mercado Pago access token is missing.");
        }
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json");
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                request.header("X-Idempotency-Key", idempotencyKey);
            }
            if ("POST".equals(method)) {
                request.POST(body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
            } else {
                request.GET();
            }

            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JSONObject json = parse(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String code = providerErrorCode(json);
                String detail = providerErrorDetail(json);
                throw new IllegalStateException("Mercado Pago Orders API returned HTTP "
                        + response.statusCode() + " (" + code + ")"
                        + (detail == null ? "." : " detail=" + detail + "."));
            }
            String id = json.optString("id", "").trim();
            if (id.isBlank()) throw new IllegalStateException("Mercado Pago order response has no id.");
            return new RemoteOrder(
                    id,
                    nullIfBlank(json.optString("checkout_url", null)),
                    nullIfBlank(json.optString("status", null)),
                    nullIfBlank(json.optString("status_detail", null)),
                    nullIfBlank(json.optString("external_reference", null)),
                    nullIfBlank(json.optString("currency", null)),
                    json);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mercado Pago request was interrupted.", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Mercado Pago request failed.", e);
        }
    }

    static String providerErrorCode(JSONObject json) {
        if (json == null) return "provider_error";
        String direct = firstNonBlank(
                json.optString("code", null),
                json.optString("error", null));
        if (direct != null) return direct;

        JSONObject detail = firstObject(json.optJSONArray("details"));
        if (detail == null) detail = firstObject(json.optJSONArray("errors"));
        if (detail != null) {
            String nested = firstNonBlank(
                    detail.optString("code", null),
                    detail.optString("error", null));
            if (nested != null) return nested;
        }
        return "provider_error";
    }

    static String providerErrorDetail(JSONObject json) {
        if (json == null) return null;
        String direct = firstNonBlank(
                json.optString("message", null),
                json.optString("cause", null));
        if (direct != null) return safeDetail(direct);

        JSONObject detail = firstObject(json.optJSONArray("details"));
        if (detail == null) detail = firstObject(json.optJSONArray("errors"));
        if (detail == null) return null;

        String nested = firstNonBlank(
                detail.optString("message", null),
                detail.optString("description", null),
                detail.optString("field", null));
        return nested == null ? null : safeDetail(nested);
    }

    private static JSONObject firstObject(JSONArray array) {
        return array == null || array.isEmpty() ? null : array.optJSONObject(0);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static String safeDetail(String value) {
        String normalized = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (normalized.isBlank()) return null;
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }

    private static JSONObject parse(String body) {
        if (body == null || body.isBlank()) return new JSONObject();
        try { return new JSONObject(body); }
        catch (Exception e) { return new JSONObject(); }
    }

    private static String path(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{3,180}")) {
            throw new IllegalArgumentException("Invalid Mercado Pago order id.");
        }
        return value;
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record RemoteOrder(
            String id,
            String checkoutUrl,
            String status,
            String statusDetail,
            String externalReference,
            String currency,
            JSONObject raw) {}
}
