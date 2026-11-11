package cl.helvoca.ai.deepgram;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Public-list-price estimator, not an invoice. Twilio carrier charges are
 * excluded because they are unchanged between the two media-stream routes.
 */
public final class DeepgramVoiceCostEstimator {
    public static final BigDecimal DEEPGRAM_NOVA3_MULTILINGUAL_USD_PER_MINUTE =
            new BigDecimal("0.0092");
    public static final BigDecimal DEEPGRAM_AURA2_USD_PER_1000_CHARACTERS =
            new BigDecimal("0.030");
    public static final BigDecimal GEMINI_LIVE_AUDIO_INPUT_USD_PER_MINUTE =
            new BigDecimal("0.005");
    public static final BigDecimal GEMINI_LIVE_AUDIO_OUTPUT_USD_PER_MINUTE =
            new BigDecimal("0.018");
    public static final BigDecimal GEMINI_TEXT_INPUT_USD_PER_MILLION_TOKENS =
            new BigDecimal("0.75");
    public static final BigDecimal GEMINI_TEXT_OUTPUT_USD_PER_MILLION_TOKENS =
            new BigDecimal("4.50");

    private DeepgramVoiceCostEstimator() { }

    public static Estimate compare(BigDecimal audioMinutes,
                                   long ttsCharacters,
                                   long geminiTextInputTokens,
                                   long geminiTextOutputTokens) {
        if (audioMinutes == null || audioMinutes.signum() < 0
                || ttsCharacters < 0 || geminiTextInputTokens < 0 || geminiTextOutputTokens < 0) {
            throw new IllegalArgumentException("Usage values cannot be negative or null");
        }
        BigDecimal deepgramStt = audioMinutes.multiply(DEEPGRAM_NOVA3_MULTILINGUAL_USD_PER_MINUTE);
        BigDecimal deepgramTts = BigDecimal.valueOf(ttsCharacters)
                .multiply(DEEPGRAM_AURA2_USD_PER_1000_CHARACTERS)
                .divide(new BigDecimal("1000"), 12, RoundingMode.HALF_UP);
        BigDecimal geminiText = BigDecimal.valueOf(geminiTextInputTokens)
                .multiply(GEMINI_TEXT_INPUT_USD_PER_MILLION_TOKENS)
                .add(BigDecimal.valueOf(geminiTextOutputTokens)
                        .multiply(GEMINI_TEXT_OUTPUT_USD_PER_MILLION_TOKENS))
                .divide(new BigDecimal("1000000"), 12, RoundingMode.HALF_UP);
        BigDecimal hybrid = deepgramStt.add(deepgramTts).add(geminiText);
        BigDecimal live = audioMinutes.multiply(
                GEMINI_LIVE_AUDIO_INPUT_USD_PER_MINUTE.add(GEMINI_LIVE_AUDIO_OUTPUT_USD_PER_MINUTE));
        BigDecimal savings = live.subtract(hybrid);
        BigDecimal percent = live.signum() == 0 ? BigDecimal.ZERO
                : savings.multiply(new BigDecimal("100"))
                .divide(live, 4, RoundingMode.HALF_UP);
        return new Estimate(deepgramStt, deepgramTts, geminiText, hybrid, live, savings, percent,
                savings.signum() > 0);
    }

    public record Estimate(BigDecimal deepgramSttUsd,
                           BigDecimal deepgramTtsUsd,
                           BigDecimal geminiTextUsd,
                           BigDecimal hybridAiUsd,
                           BigDecimal geminiLiveAiUsd,
                           BigDecimal estimatedSavingsUsd,
                           BigDecimal estimatedSavingsPercent,
                           boolean hybridIsCheaper) { }
}
