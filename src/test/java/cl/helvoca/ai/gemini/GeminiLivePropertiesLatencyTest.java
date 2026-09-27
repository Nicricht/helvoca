package cl.helvoca.ai.gemini;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiLivePropertiesLatencyTest {

    @Test
    void lowLatencyDefaultsAreSafeAndExplicit() {
        GeminiLiveProperties properties = new GeminiLiveProperties();

        assertEquals("START_SENSITIVITY_HIGH", properties.getStartOfSpeechSensitivity());
        assertEquals(60, properties.getPrefixPaddingMs());
        assertEquals("END_SENSITIVITY_HIGH", properties.getEndOfSpeechSensitivity());
        assertEquals(500, properties.getSilenceDurationMs());
        assertTrue(properties.isHybridVadEnabled());
        assertTrue(properties.isLocalBargeInEnabled());
        assertEquals(900, properties.getLocalBargeInMeanAmplitudeThreshold());
        assertEquals(2, properties.getLocalBargeInSpeechFrames());
        assertEquals(25, properties.getLocalBargeInReleaseFrames());
        assertEquals(1800, properties.getLocalBargeInRecentAssistantAudioMs());
    }

    @Test
    void latencyKnobsClampUnsafeValuesAndNormalizeEnums() {
        GeminiLiveProperties properties = new GeminiLiveProperties();

        properties.setStartOfSpeechSensitivity("START_SENSITIVITY_LOW");
        properties.setEndOfSpeechSensitivity("END_SENSITIVITY_HIGH");
        properties.setPrefixPaddingMs(-10);
        properties.setSilenceDurationMs(10);
        properties.setHybridVadEnabled(false);
        properties.setLocalBargeInEnabled(false);
        properties.setLocalBargeInMeanAmplitudeThreshold(1);
        properties.setLocalBargeInSpeechFrames(0);
        properties.setLocalBargeInReleaseFrames(0);
        properties.setLocalBargeInRecentAssistantAudioMs(1);

        assertEquals("START_SENSITIVITY_LOW", properties.getStartOfSpeechSensitivity());
        assertEquals("END_SENSITIVITY_HIGH", properties.getEndOfSpeechSensitivity());
        assertEquals(0, properties.getPrefixPaddingMs());
        assertEquals(100, properties.getSilenceDurationMs());
        assertFalse(properties.isHybridVadEnabled());
        assertFalse(properties.isLocalBargeInEnabled());
        assertEquals(100, properties.getLocalBargeInMeanAmplitudeThreshold());
        assertEquals(1, properties.getLocalBargeInSpeechFrames());
        assertEquals(1, properties.getLocalBargeInReleaseFrames());
        assertEquals(250, properties.getLocalBargeInRecentAssistantAudioMs());

        properties.setStartOfSpeechSensitivity("invalid");
        properties.setEndOfSpeechSensitivity("invalid");
        properties.setPrefixPaddingMs(5000);
        properties.setSilenceDurationMs(5000);
        properties.setLocalBargeInMeanAmplitudeThreshold(50000);
        properties.setLocalBargeInSpeechFrames(100);
        properties.setLocalBargeInReleaseFrames(500);
        properties.setLocalBargeInRecentAssistantAudioMs(50000);

        assertEquals("START_SENSITIVITY_HIGH", properties.getStartOfSpeechSensitivity());
        assertEquals("END_SENSITIVITY_HIGH", properties.getEndOfSpeechSensitivity());
        assertEquals(1000, properties.getPrefixPaddingMs());
        assertEquals(2000, properties.getSilenceDurationMs());
        assertEquals(20000, properties.getLocalBargeInMeanAmplitudeThreshold());
        assertEquals(20, properties.getLocalBargeInSpeechFrames());
        assertEquals(100, properties.getLocalBargeInReleaseFrames());
        assertEquals(10000, properties.getLocalBargeInRecentAssistantAudioMs());
    }
}
