package cl.helvoca.ai.deepgram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeepgramVoicePropertiesTest {
    @Test
    void remainsUnavailableWithoutExplicitEnablementCredentialsAndDataApproval() {
        DeepgramVoiceProperties properties = new DeepgramVoiceProperties();
        assertFalse(properties.ready());

        properties.setApiKey("test-key");
        assertFalse(properties.ready(), "A key alone must not enable external speech processing");

        properties.setEnabled(true);
        assertFalse(properties.ready(), "Explicit data-processing approval is still required");

        properties.setDataProcessingApproved(true);
        assertTrue(properties.ready());
    }

    @Test
    void rejectsUnapprovedModelsAndUnsafeEndpoints() {
        DeepgramVoiceProperties properties = new DeepgramVoiceProperties();
        properties.setEnabled(true);
        properties.setDataProcessingApproved(true);
        properties.setApiKey("test-key");
        properties.setSttEndpoint("http://example.com/listen");
        assertFalse(properties.ready(), "STT must use TLS WebSockets");

        properties.setSttEndpoint("wss://api.deepgram.com/v1/listen");
        properties.setTtsModel("aura-asteria-en");
        assertFalse(properties.ready(), "The configured Spanish TTS model must be explicit");
    }
}
