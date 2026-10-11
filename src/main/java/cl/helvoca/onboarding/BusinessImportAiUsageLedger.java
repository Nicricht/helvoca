package cl.helvoca.onboarding;

import cl.helvoca.security.TenantProvider;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Tenant-scoped provider usage evidence. Never records input documents,
 * prompts, credentials or the model response text. Invoice cost is unknown:
 * only an explicitly priced estimate can be derived from token counters.
 */
@Service
public class BusinessImportAiUsageLedger {
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private final JdbcTemplate jdbc;
    private final TenantProvider tenant;

    @Value("${app.onboarding.import-ai.pricing.model:}")
    private String pricedModel = "";
    @Value("${app.onboarding.import-ai.pricing.input-usd-per-million:0}")
    private BigDecimal inputRate = BigDecimal.ZERO;
    @Value("${app.onboarding.import-ai.pricing.cached-input-usd-per-million:0}")
    private BigDecimal cachedRate = BigDecimal.ZERO;
    @Value("${app.onboarding.import-ai.pricing.output-usd-per-million:0}")
    private BigDecimal outputRate = BigDecimal.ZERO;

    public BusinessImportAiUsageLedger(JdbcTemplate jdbc, TenantProvider tenant) {
        this.jdbc = jdbc;
        this.tenant = tenant;
    }

    @Transactional
    public void started(UUID attemptId, String model) {
        insert(attemptId, "STARTED", model, null, null, null);
    }

    @Transactional
    public void uncertain(UUID attemptId, String model) {
        insert(attemptId, "UNCERTAIN", model, null, null, null);
    }

    @Transactional
    public void received(UUID attemptId, String model, HttpResponse<String> response) {
        String providerRequestId = response.headers() == null ? null
                : response.headers().firstValue("x-request-id").orElse(null);
        insert(attemptId, "RESPONSE", model, response.statusCode(), response.body(),
                providerRequestId);
    }

    @Transactional
    public void receivedGemini(UUID attemptId, String requestedModel, HttpResponse<String> response) {
        // Adapt native Gemini token usage to the existing tenant-scoped receipt,
        // WITHOUT persisting the input image, output text or a private API key.
        JSONObject root = responseJson(response.body());
        JSONObject usage = root == null ? null : root.optJSONObject("usageMetadata");
        JSONObject canonical = new JSONObject();
        if (root != null) canonical.put("model", root.optString("modelVersion", requestedModel));
        if (usage != null && usage.has("promptTokenCount")
                && usage.has("candidatesTokenCount")) {
            canonical.put("usage", new JSONObject()
                    .put("input_tokens", usage.optLong("promptTokenCount", -1))
                    .put("output_tokens", usage.optLong("candidatesTokenCount", -1)));
        }
        String providerRequestId = response.headers() == null ? null
                : response.headers().firstValue("x-request-id").orElse(null);
        insert(attemptId, "RESPONSE", requestedModel, response.statusCode(),
                canonical.toString(), providerRequestId);
    }

    private void insert(UUID attemptId, String phase, String model, Integer httpStatus,
                        String body, String providerRequestId) {
        // The business id is resolved from the authenticated JWT on every write.
        UUID businessId = tenant.requireBusinessId();
        JSONObject root = responseJson(body);
        String responseId = root == null ? null : optionalString(root, "id", 180);
        String responseModel = root == null ? null : optionalString(root, "model", 100);
        Tokens tokens = root == null ? null : parseTokens(root.optJSONObject("usage"));
        BigDecimal estimate = tokens == null ? null : estimatedUsd(model, responseModel, tokens);

        jdbc.update("""
                INSERT INTO public.business_import_ai_provider_usage_event (
                    business_id, attempt_id, phase, requested_model, provider_model,
                    provider_response_id, provider_request_id, http_status,
                    input_tokens, cached_input_tokens, output_tokens, estimated_cost_usd
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (business_id, attempt_id, phase) DO NOTHING
                """,
                businessId, attemptId, phase, model, responseModel,
                responseId, providerRequestId, httpStatus,
                tokens == null ? null : tokens.input(),
                tokens == null ? null : tokens.cached(),
                tokens == null ? null : tokens.output(), estimate);
    }

    private static JSONObject responseJson(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return new JSONObject(body);
        } catch (RuntimeException invalid) {
            // An invalid provider body is still auditable as unknown usage.
            return null;
        }
    }

    private static String optionalString(JSONObject root, String key, int maxLength) {
        String value = root.optString(key, "").trim();
        if (value.isEmpty()) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    static Tokens parseTokens(JSONObject usage) {
        if (usage == null) return null;
        long input = usage.optLong("input_tokens", -1);
        long output = usage.optLong("output_tokens", -1);
        if (input < 0 || output < 0) return null;
        JSONObject details = usage.optJSONObject("input_tokens_details");
        Long cached = details == null ? null : details.optLong("cached_tokens", -1);
        if (cached != null && (cached < 0 || cached > input)) return null;
        return new Tokens(input, cached, output);
    }

    private BigDecimal estimatedUsd(String requestedModel, String returnedModel, Tokens tokens) {
        // An operator must have configured current exact-model pricing.
        // Never silently price a model alias, an unknown model or changed rates.
        if (!pricedModel.equals(requestedModel) || !pricedModel.equals(returnedModel)
                || inputRate.signum() <= 0 || cachedRate.signum() <= 0
                || outputRate.signum() <= 0) return null;
        long cached = tokens.cached() == null ? 0 : tokens.cached();
        BigDecimal amount = BigDecimal.valueOf(tokens.input() - cached).multiply(inputRate)
                .add(BigDecimal.valueOf(cached).multiply(cachedRate))
                .add(BigDecimal.valueOf(tokens.output()).multiply(outputRate));
        return amount.divide(MILLION, 8, RoundingMode.HALF_UP);
    }

    record Tokens(long input, Long cached, long output) {}
}
