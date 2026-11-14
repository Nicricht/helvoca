package cl.helvoca.ai.gemini;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Receives Google Live API usageMetadata for future reconciliation.
 * Provider events can be replayed, partial or have different aggregation
 * semantics. Preserve each snapshot individually; never add the numbers and
 * claim an invoice total without Google Billing's authoritative report.
 */
final class GeminiLiveUsageMeter {
    private static final Logger log = LoggerFactory.getLogger(GeminiLiveUsageMeter.class);
    private final UUID callId;
    private final UUID businessId;
    private final String model;
    private final AtomicInteger events = new AtomicInteger();

    GeminiLiveUsageMeter(UUID callId, UUID businessId, String model) {
        this.callId = callId;
        this.businessId = businessId;
        this.model = model;
    }

    void record(JSONObject usageMetadata) {
        if (usageMetadata == null) return;
        Snapshot snapshot = parse(usageMetadata);
        int sequence = events.incrementAndGet();
        // Never log the raw JSON: provider payloads can contain unexpected
        // fields with caller PII, API credentials or transcribed content.
        log.info("GEMINI_LIVE_USAGE_SNAPSHOT call_id={} business_id={} model={} sequence={} "
                        + "prompt_tokens={} response_tokens={} thoughts_tokens={} cached_tokens={} "
                        + "total_tokens={} input_audio_tokens={} output_audio_tokens={} "
                        + "input_text_tokens={} output_text_tokens={} has_total_token_count={} "
                        + "kind=PROVIDER_EVENT_NOT_INVOICE",
                callId, businessId, model, sequence,
                snapshot.promptTokens(), snapshot.responseTokens(), snapshot.thoughtsTokens(),
                snapshot.cachedTokens(), snapshot.totalTokens(),
                snapshot.inputAudioTokens(), snapshot.outputAudioTokens(),
                snapshot.inputTextTokens(), snapshot.outputTextTokens(), snapshot.hasTotalTokenCount());
    }

    void logFinalSummary() {
        int count = events.get();
        log.info("GEMINI_LIVE_USAGE_SESSION call_id={} business_id={} model={} "
                        + "provider_usage_events={} usage_reported={} billed_cost_usd=UNKNOWN "
                        + "kind=PROVIDER_METADATA_NOT_BILLING_INVOICE",
                callId, businessId, model, count, count > 0);
    }

    int snapshotCount() {
        return events.get();
    }

    static Snapshot parse(JSONObject metadata) {
        JSONObject source = metadata == null ? new JSONObject() : metadata;
        return new Snapshot(
                nonnegative(source, "promptTokenCount"),
                nonnegative(source, "responseTokenCount"),
                nonnegative(source, "thoughtsTokenCount"),
                nonnegative(source, "cachedContentTokenCount"),
                nonnegative(source, "totalTokenCount"),
                modalityTokens(source.optJSONArray("promptTokensDetails"), "AUDIO"),
                modalityTokens(source.optJSONArray("responseTokensDetails"), "AUDIO"),
                modalityTokens(source.optJSONArray("promptTokensDetails"), "TEXT"),
                modalityTokens(source.optJSONArray("responseTokensDetails"), "TEXT"),
                source.has("totalTokenCount"));
    }

    private static long nonnegative(JSONObject value, String key) {
        return Math.max(0L, value.optLong(key, 0L));
    }

    private static long modalityTokens(JSONArray details, String modality) {
        if (details == null) return 0L;
        long total = 0L;
        for (int i = 0; i < details.length(); i++) {
            JSONObject detail = details.optJSONObject(i);
            if (detail != null && modality.equalsIgnoreCase(detail.optString("modality", ""))) {
                total += nonnegative(detail, "tokenCount");
            }
        }
        return total;
    }

    record Snapshot(long promptTokens, long responseTokens, long thoughtsTokens,
                    long cachedTokens, long totalTokens, long inputAudioTokens,
                    long outputAudioTokens, long inputTextTokens, long outputTextTokens,
                    boolean hasTotalTokenCount) {}
}
