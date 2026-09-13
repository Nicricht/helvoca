package cl.helvoca.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentVoiceProfileTest {

    @Test
    void neutralProfileNormalizesToOpenAiSafeCanonicalVoice() {
        assertEquals("marin", AgentVoiceProfile.normalizeForStorage("natural"));
        assertEquals("cedar", AgentVoiceProfile.normalizeForStorage("professional"));
        assertEquals("coral", AgentVoiceProfile.normalizeForStorage("friendly"));
    }

    @Test
    void canonicalSelectionResolvesPerProvider() {
        assertEquals("marin", AgentVoiceProfile.resolveOpenAi("marin", "alloy"));
        assertEquals("Aoede", AgentVoiceProfile.resolveGemini("marin", "Kore"));

        assertEquals("cedar", AgentVoiceProfile.resolveOpenAi("professional", "alloy"));
        assertEquals("Charon", AgentVoiceProfile.resolveGemini("professional", "Kore"));
    }

    @Test
    void legacyGeminiVoiceRemainsReadableForGeminiButFallsBackForOpenAi() {
        assertEquals("Kore", AgentVoiceProfile.resolveGemini("Kore", "Aoede"));
        assertEquals("alloy", AgentVoiceProfile.resolveOpenAi("Kore", "alloy"));
    }

    @Test
    void unknownSelectionIsRejectedOnSave() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> AgentVoiceProfile.normalizeForStorage("voice-that-does-not-exist"));
        assertEquals("Unsupported agent voice", error.getMessage());
    }

    @Test
    void everyPublishedProfileHasProviderMappings() {
        assertEquals(10, AgentVoiceProfile.catalog().size());
        for (AgentVoiceProfile profile : AgentVoiceProfile.catalog()) {
            assertFalse(profile.code().isBlank());
            assertFalse(profile.displayName().isBlank());
            assertFalse(profile.openAiVoice().isBlank());
            assertFalse(profile.geminiVoice().isBlank());
        }
    }
}
