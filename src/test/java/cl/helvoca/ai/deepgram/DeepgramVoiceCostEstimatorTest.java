package cl.helvoca.ai.deepgram;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DeepgramVoiceCostEstimatorTest {
    @Test
    void deterministicPublicAnswerHasAnEstimatedFortyPercentAiCostReduction() {
        var estimate = DeepgramVoiceCostEstimator.compare(
                new BigDecimal("1.0"), 150, 0, 0);

        assertEquals(new BigDecimal("0.0092"), estimate.deepgramSttUsd());
        assertEquals(new BigDecimal("0.004500000000"), estimate.deepgramTtsUsd());
        assertEquals(new BigDecimal("0.013700000000"), estimate.hybridAiUsd());
        assertEquals(new BigDecimal("0.0230"), estimate.geminiLiveAiUsd());
        assertTrue(estimate.hybridIsCheaper());
        assertEquals(new BigDecimal("40.4348"), estimate.estimatedSavingsPercent());
    }

    @Test
    void includesGeminiTextReasoningAndCanRejectLongExpensiveReplies() {
        var hybrid = DeepgramVoiceCostEstimator.compare(
                new BigDecimal("1.0"), 150, 500, 250);
        assertEquals(new BigDecimal("0.015200000000"), hybrid.hybridAiUsd());
        assertTrue(hybrid.hybridIsCheaper());

        var longReply = DeepgramVoiceCostEstimator.compare(
                new BigDecimal("1.0"), 500, 500, 250);
        assertFalse(longReply.hybridIsCheaper());
    }

    @Test
    void rejectsNegativeUsage() {
        assertThrows(IllegalArgumentException.class,
                () -> DeepgramVoiceCostEstimator.compare(new BigDecimal("-1"), 10, 0, 0));
    }
}
