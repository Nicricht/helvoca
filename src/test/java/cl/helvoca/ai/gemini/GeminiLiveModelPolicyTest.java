package cl.helvoca.ai.gemini;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiLiveModelPolicyTest {
    @Test
    void onlySupportedBidirectionalModelsHaveOfflineEligibility() {
        assertEquals("STABLE", GeminiLiveModelPolicy.tier("gemini-3.8-live"));
        assertEquals("LEGACY_PREVIEW", GeminiLiveModelPolicy.tier("gemini-3.1-flash-live-preview"));
        assertEquals("UNSUPPORTED", GeminiLiveModelPolicy.tier("gemini-3.8-live-extended-thinking"));
        assertEquals("UNSUPPORTED", GeminiLiveModelPolicy.tier("gemini-3.8-flash"));
        assertEquals("UNSUPPORTED", GeminiLiveModelPolicy.tier(null));
        assertEquals("UNSUPPORTED", GeminiLiveModelPolicy.tier("models/gemini-3.8-live"));
        assertEquals("UNSUPPORTED", GeminiLiveModelPolicy.tier(" gemini-3.8-live "));
        assertTrue(GeminiLiveModelPolicy.supported(GeminiLiveModelPolicy.DEFAULT_MODEL));
        assertTrue(GeminiLiveModelPolicy.supported(GeminiLiveModelPolicy.LEGACY_MODEL));
        assertFalse(GeminiLiveModelPolicy.supported("gemini-3.8-flash"));
    }

    @Test
    void onlyOfficialNonRedirectableKeyDestinationIsAccepted() {
        assertTrue(GeminiLiveModelPolicy.approvedEndpoint(GeminiLiveModelPolicy.OFFICIAL_WEBSOCKET_URL));
        assertTrue(GeminiLiveModelPolicy.approvedEndpoint(" " + GeminiLiveModelPolicy.OFFICIAL_WEBSOCKET_URL + " "));
        for (String candidate : new String[]{
                "wss://other.example/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent",
                GeminiLiveModelPolicy.OFFICIAL_WEBSOCKET_URL + "?key=stolen",
                GeminiLiveModelPolicy.OFFICIAL_WEBSOCKET_URL + "#fragment",
                "ws://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent",
                "https://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent",
                "", " "
        }) {
            assertFalse(GeminiLiveModelPolicy.approvedEndpoint(candidate), candidate);
        }
        assertFalse(GeminiLiveModelPolicy.approvedEndpoint(null));
    }

    @Test
    void privateConstructorIsNotAnAlternateMutableConfigurationPath() throws Exception {
        var constructor = GeminiLiveModelPolicy.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
