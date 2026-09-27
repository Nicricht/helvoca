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
    void legacyDespinaSelectionMigratesToYouthfulFemaleProfile() {
        assertEquals("seductive_female", AgentVoiceProfile.normalizeForStorage("Despina"));
        assertEquals("Leda", AgentVoiceProfile.resolveGemini("Despina", "Kore"));
        assertEquals("coral", AgentVoiceProfile.resolveOpenAi("Despina", "alloy"));
    }

    @Test
    void legacyGlobalDespinaFallbackAlsoMigratesToLeda() {
        assertEquals("Leda", AgentVoiceProfile.resolveGemini(null, "Despina"));
        assertEquals("Leda", AgentVoiceProfile.resolveGemini("   ", "Despina"));
        assertEquals("Kore", AgentVoiceProfile.resolveGemini(null, "Kore"));
        assertNull(AgentVoiceProfile.resolveGemini(null, null));
    }

    @Test
    void commercialVoiceProfilesRemainDistinctAndResolvePerProvider() {
        assertEquals("seductive_female", AgentVoiceProfile.normalizeForStorage("seductive_female"));
        assertEquals("Leda", AgentVoiceProfile.resolveGemini("seductive_female", "Kore"));
        assertEquals("Joven chilena", AgentVoiceProfile.SEDUCTIVE_FEMALE.displayName());
        assertTrue(AgentVoiceProfile.SEDUCTIVE_FEMALE.description().contains("joven-adulta"));
        assertEquals("coral", AgentVoiceProfile.resolveOpenAi("seductive_female", "alloy"));

        assertEquals("seductive_male", AgentVoiceProfile.normalizeForStorage("seductive_male"));
        assertEquals("Enceladus", AgentVoiceProfile.resolveGemini("seductive_male", "Kore"));
        assertEquals("cedar", AgentVoiceProfile.resolveOpenAi("seductive_male", "alloy"));
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
        assertEquals(12, AgentVoiceProfile.catalog().size());
        for (AgentVoiceProfile profile : AgentVoiceProfile.catalog()) {
            assertFalse(profile.code().isBlank());
            assertFalse(profile.displayName().isBlank());
            assertFalse(profile.openAiVoice().isBlank());
            assertFalse(profile.geminiVoice().isBlank());
        }
    }
}
