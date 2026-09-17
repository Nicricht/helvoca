package cl.helvoca.ai.gemini;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeminiLiveModelBaselineTest {

    @Test
    void defaultsToGemini38LiveForLowLatencyVoice() {
        GeminiLiveProperties properties = new GeminiLiveProperties();

        assertEquals("gemini-3.8-live", properties.getModel());
    }
}
