package cl.helvoca.ai.gemini;

import cl.helvoca.voice.VoiceCallRouter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiLiveRuntimeReadinessTest {
    @Test
    void reportsExplicitConfiguredModelAndSelectionWithoutCredentialsOrNetworkCalls() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setEnabled(true);
        properties.setApiKey("a-secret-not-for-logs");
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        when(router.readiness()).thenReturn(new VoiceCallRouter.VoiceReadiness(true, "gemini", List.of()));
        GeminiLiveRuntimeReadiness readiness = new GeminiLiveRuntimeReadiness(properties, router);

        GeminiLiveRuntimeReadiness.Snapshot result = readiness.snapshot();
        assertEquals("gemini-3.8-live", result.configuredModel());
        assertEquals("STABLE", result.modelTier());
        assertTrue(result.enabled());
        assertTrue(result.apiKeyConfigured());
        assertTrue(result.endpointApproved());
        assertTrue(result.localConfigurationReady());
        assertEquals("GEMINI", result.routerSelection());
        assertFalse(result.toString().contains("a-secret"));
        assertFalse(result.toString().contains("generativelanguage.googleapis.com"));
        readiness.run(mock(ApplicationArguments.class));
        verify(router, times(2)).readiness();
        verifyNoMoreInteractions(router);
    }

    @Test
    void reportsLegacyOverrideAndNonGeminiSelectionWithoutClaimingActiveGemini() {
        GeminiLiveProperties props = new GeminiLiveProperties();
        props.setEnabled(true);
        props.setApiKey("not-the-real-token");
        props.setModel("gemini-3.1-flash-live-preview");
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        when(router.readiness()).thenReturn(new VoiceCallRouter.VoiceReadiness(true, "openai-live", List.of()));

        var status = new GeminiLiveRuntimeReadiness(props, router).snapshot();
        assertEquals("gemini-3.1-flash-live-preview", status.configuredModel());
        assertEquals("LEGACY_PREVIEW", status.modelTier());
        assertEquals("OTHER", status.routerSelection());
        assertTrue(status.localConfigurationReady());
        assertFalse(status.toString().contains("not-the-real-token"));
    }

    @Test
    void blocksUnknownModelAndInvalidEndpointWithoutExposingTheirValues() {
        GeminiLiveProperties props = new GeminiLiveProperties();
        props.setEnabled(true);
        props.setApiKey("secret-token");
        props.setModel("malicious-secret-model");
        props.setWebsocketUrl("wss://evil.example/?key=secret-token");
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        when(router.readiness()).thenReturn(new VoiceCallRouter.VoiceReadiness(false, null, List.of()));

        var status = new GeminiLiveRuntimeReadiness(props, router).snapshot();
        assertNull(status.configuredModel());
        assertEquals("UNSUPPORTED", status.modelTier());
        assertTrue(status.apiKeyConfigured());
        assertFalse(status.endpointApproved());
        assertFalse(status.localConfigurationReady());
        assertEquals("NONE", status.routerSelection());
        assertFalse(status.toString().contains("secret-token"));
        assertFalse(status.toString().contains("evil.example"));
        assertFalse(props.ready());
    }

    @Test
    void requiresAllLocalConfigurationInputsAndDoesNotTestRemoteEntitlements() {
        GeminiLiveProperties props = new GeminiLiveProperties();
        assertFalse(props.ready());
        props.setEnabled(true);
        assertFalse(props.ready());
        props.setApiKey(null);
        assertFalse(props.ready());
        props.setApiKey("fake-key");
        assertTrue(props.ready());
        props.setModel(null);
        assertFalse(props.ready());
        props.setModel(GeminiLiveModelPolicy.DEFAULT_MODEL);
        props.setVoice(" ");
        assertFalse(props.ready());
        props.setVoice("Sulafat");
        props.setWebsocketUrl("wss://fake-host.example");
        assertFalse(props.ready());
        props.setWebsocketUrl(GeminiLiveModelPolicy.OFFICIAL_WEBSOCKET_URL);
        assertTrue(props.ready());

        VoiceCallRouter router = mock(VoiceCallRouter.class);
        when(router.readiness()).thenReturn(new VoiceCallRouter.VoiceReadiness(true, "gemini", List.of()));
        var status = new GeminiLiveRuntimeReadiness(props, router).snapshot();
        assertTrue(status.localConfigurationReady());
        props.setEnabled(false);
        assertFalse(new GeminiLiveRuntimeReadiness(props, router).snapshot().localConfigurationReady());
    }

    @Test
    void defaultYamlMatchesStableModelWithSafeEnvironmentOverride() throws Exception {
        var source = new org.springframework.boot.env.YamlPropertySourceLoader().load(
                "helvoca-gemini-default",
                new org.springframework.core.io.ClassPathResource("application.yml")).getFirst();
        assertEquals("${GEMINI_LIVE_MODEL:gemini-3.8-live}",
                source.getProperty("app.gemini.live.model"));
        assertEquals("${GEMINI_LIVE_ENABLED:false}",
                source.getProperty("app.gemini.live.enabled"));
        assertEquals("${GEMINI_LIVE_PROBE_ON_STARTUP:false}",
                "${GEMINI_LIVE_PROBE_ON_STARTUP:false}");
    }
}
