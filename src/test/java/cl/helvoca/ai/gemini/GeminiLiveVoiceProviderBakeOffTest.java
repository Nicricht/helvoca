package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONObject;
import java.net.http.HttpClient;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallCertificationService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiLiveVoiceProviderBakeOffTest {

    @Test
    void signedBakeOffVoiceWinsOnlyForThatCall() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("gemini-3.8-live");
        properties.setVoice("Leda");

        RealtimeToolService tools = mock(RealtimeToolService.class);
        GeminiLiveVoiceProvider provider = new GeminiLiveVoiceProvider(
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry());

        RealtimeCallContext normal = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-normal");
        when(tools.agentVoice(normal, null)).thenReturn("seductive_female");

        RealtimeCallContext bakeOff = new RealtimeCallContext(
                normal.callId(), normal.businessId(), normal.customerId(),
                normal.callerNumber(), normal.destinationNumber(), "MZ-bakeoff", "Sadachbia");
        when(tools.agentVoice(bakeOff, null)).thenReturn("seductive_female");

        assertEquals("Sulafat", provider.sessionProperties(normal).getVoice());
        assertEquals("Sadachbia", provider.sessionProperties(bakeOff).getVoice());
    }

    @Test
    void bakeOffContextIsNotClassifiedAsBookingCertificationSession() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        GeminiLiveVoiceProvider provider = new GeminiLiveVoiceProvider(
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry());

        RealtimeCallContext bakeOff = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56966939611", "+14355652512", "MZ-bakeoff", "Achird");
        RealtimeCallContext normal = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-normal");

        assertEquals(false, provider.certificationSession(bakeOff));
        assertEquals(false, provider.certificationSession(normal));
    }

    @Test
    void unknownOverrideCannotBypassCuratedCatalog() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("gemini-3.8-live");
        properties.setVoice("Leda");

        RealtimeToolService tools = mock(RealtimeToolService.class);
        GeminiLiveVoiceProvider provider = new GeminiLiveVoiceProvider(
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry());

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-test", "NotARealCandidate");
        when(tools.agentVoice(context, null)).thenReturn("seductive_female");

        assertEquals("Sulafat", provider.sessionProperties(context).getVoice());
    }

    @Test
    void eachSessionSnapshotsOperatorVadInterruptionAndLatencyInsteadOfResettingDefaults() {
        GeminiLiveProperties settings = new GeminiLiveProperties();
        settings.setEnabled(true);
        settings.setApiKey("offline-test-only-key");
        settings.setStartOfSpeechSensitivity("START_SENSITIVITY_LOW");
        settings.setPrefixPaddingMs(120);
        settings.setEndOfSpeechSensitivity("END_SENSITIVITY_LOW");
        settings.setSilenceDurationMs(910);
        settings.setHybridVadEnabled(false);
        settings.setLocalBargeInEnabled(false);
        settings.setLocalBargeInMeanAmplitudeThreshold(2300);
        settings.setLocalBargeInSpeechFrames(4);
        settings.setLocalBargeInReleaseFrames(35);
        settings.setLocalBargeInRecentAssistantAudioMs(2200);
        settings.setToolLatencyBudgetMs(1900);
        settings.setToolBatchLatencyBudgetMs(2400);
        settings.setResponseLatencyBudgetMs(4800);
        var tools = mock(RealtimeToolService.class);
        GeminiLiveVoiceProvider provider = new GeminiLiveVoiceProvider(settings, tools,
                mock(CallTranscriptService.class), mock(CallSummaryService.class),
                mock(CallLifecycleService.class), mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry());

        GeminiLiveProperties first = provider.sessionProperties(null);
        assertNotSame(settings, first);
        assertEquals("START_SENSITIVITY_LOW", first.getStartOfSpeechSensitivity());
        assertEquals(120, first.getPrefixPaddingMs());
        assertEquals("END_SENSITIVITY_LOW", first.getEndOfSpeechSensitivity());
        assertEquals(910, first.getSilenceDurationMs());
        assertFalse(first.isHybridVadEnabled());
        assertFalse(first.isLocalBargeInEnabled());
        assertEquals(2300, first.getLocalBargeInMeanAmplitudeThreshold());
        assertEquals(4, first.getLocalBargeInSpeechFrames());
        assertEquals(35, first.getLocalBargeInReleaseFrames());
        assertEquals(2200, first.getLocalBargeInRecentAssistantAudioMs());
        assertEquals(1900, first.getToolLatencyBudgetMs());
        assertEquals(2400, first.getToolBatchLatencyBudgetMs());
        assertEquals(4800, first.getResponseLatencyBudgetMs());
        assertEquals("offline-test-only-key", first.getApiKey());

        first.setSilenceDurationMs(1400);
        first.setHybridVadEnabled(true);
        first.setToolLatencyBudgetMs(2900);
        GeminiLiveProperties second = provider.sessionProperties(null);
        assertEquals(910, second.getSilenceDurationMs());
        assertFalse(second.isHybridVadEnabled());
        assertEquals(1900, second.getToolLatencyBudgetMs());
        assertEquals(910, settings.getSilenceDurationMs());
        verifyNoInteractions(tools);
    }

    @Test
    void sessionSnapshotUsesClampedValuesAndGeminiSetupReflectsConfiguredVadOffline() {
        GeminiLiveProperties settings = new GeminiLiveProperties();
        settings.setEnabled(false);
        settings.setApiKey("");
        settings.setStartOfSpeechSensitivity("START_SENSITIVITY_LOW");
        settings.setPrefixPaddingMs(-400);
        settings.setEndOfSpeechSensitivity("END_SENSITIVITY_LOW");
        settings.setSilenceDurationMs(99999);
        settings.setLocalBargeInMeanAmplitudeThreshold(-1);
        settings.setLocalBargeInSpeechFrames(999);
        settings.setLocalBargeInReleaseFrames(-1);
        settings.setLocalBargeInRecentAssistantAudioMs(99999);
        settings.setToolLatencyBudgetMs(-1);
        settings.setToolBatchLatencyBudgetMs(99999);
        settings.setResponseLatencyBudgetMs(0);
        RealtimeToolService tools = mock(RealtimeToolService.class);
        GeminiLiveVoiceProvider provider = new GeminiLiveVoiceProvider(settings, tools,
                mock(CallTranscriptService.class), mock(CallSummaryService.class),
                mock(CallLifecycleService.class), mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry());
        GeminiLiveProperties sessionConfig = provider.sessionProperties(null);
        assertFalse(sessionConfig.ready(), "Missing key/disabled provider must stay disabled per call");
        assertEquals(0, sessionConfig.getPrefixPaddingMs());
        assertEquals(2000, sessionConfig.getSilenceDurationMs());
        assertEquals(100, sessionConfig.getLocalBargeInMeanAmplitudeThreshold());
        assertEquals(20, sessionConfig.getLocalBargeInSpeechFrames());
        assertEquals(1, sessionConfig.getLocalBargeInReleaseFrames());
        assertEquals(10000, sessionConfig.getLocalBargeInRecentAssistantAudioMs());
        assertEquals(100, sessionConfig.getToolLatencyBudgetMs());
        assertEquals(30000, sessionConfig.getToolBatchLatencyBudgetMs());
        assertEquals(250, sessionConfig.getResponseLatencyBudgetMs());

        var ctx = new RealtimeCallContext(UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355551101", "MZ-offline");
        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                ctx, mock(VoiceTransportSession.class), sessionConfig, tools,
                mock(CallTranscriptService.class), mock(CallSummaryService.class),
                mock(CallLifecycleService.class), mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(), HttpClient.newHttpClient());
        JSONObject detection = session.buildSetup().getJSONObject("setup")
                .getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection");
        assertEquals("START_SENSITIVITY_LOW", detection.getString("startOfSpeechSensitivity"));
        assertEquals(0, detection.getInt("prefixPaddingMs"));
        assertEquals("END_SENSITIVITY_LOW", detection.getString("endOfSpeechSensitivity"));
        assertEquals(2000, detection.getInt("silenceDurationMs"));
        assertThrows(IllegalStateException.class, session::start,
                "An unconfigured session must be rejected before any WebSocket request");
    }
}
