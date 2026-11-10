package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolDefinitions;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallCertificationService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiLiveLowLatencySetupTest {

    @Test
    void latencyBudgetsHaveFastProductionDefaultsAndSafeBounds() {
        GeminiLiveProperties properties = new GeminiLiveProperties();

        assertEquals(1200, properties.getToolLatencyBudgetMs());
        assertEquals(1800, properties.getToolBatchLatencyBudgetMs());
        assertEquals(3000, properties.getResponseLatencyBudgetMs());

        properties.setToolLatencyBudgetMs(1);
        properties.setToolBatchLatencyBudgetMs(99_999);
        properties.setResponseLatencyBudgetMs(1);

        assertEquals(100, properties.getToolLatencyBudgetMs());
        assertEquals(30_000, properties.getToolBatchLatencyBudgetMs());
        assertEquals(250, properties.getResponseLatencyBudgetMs());
    }

    @Test
    void setupAllowsNaturalPausesWithoutDisablingBargeIn() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("gemini-3.1-flash-live-preview");
        properties.setVoice("Kore");

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-test");
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        when(tools.toolDefinitions(context)).thenReturn(RealtimeToolDefinitions.all());

        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                mock(VoiceTransportSession.class),
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        JSONObject setup = session.buildSetup().getJSONObject("setup");
        JSONObject realtimeInput = setup.getJSONObject("realtimeInputConfig");
        JSONObject activity = realtimeInput.getJSONObject("automaticActivityDetection");

        assertFalse(activity.getBoolean("disabled"));
        assertEquals("START_SENSITIVITY_HIGH", activity.getString("startOfSpeechSensitivity"));
        assertEquals(60, activity.getInt("prefixPaddingMs"));
        assertEquals("END_SENSITIVITY_LOW", activity.getString("endOfSpeechSensitivity"));
        assertEquals(900, activity.getInt("silenceDurationMs"));
        assertEquals("START_OF_ACTIVITY_INTERRUPTS", realtimeInput.getString("activityHandling"));
        assertFalse(setup.has("proactivity"));
        assertFalse(setup.has("contextWindowCompression"), "disabled by default");
    }
    @Test
    void compressionIsOptInUsesBoundedSlidingWindowAndPreservesSulafat() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        assertFalse(properties.isContextWindowCompressionEnabled());
        assertEquals(25_000, properties.getContextWindowCompressionTriggerTokens());
        assertEquals(12_000, properties.getContextWindowCompressionTargetTokens());
        properties.setContextWindowCompressionEnabled(true);
        properties.setApiKey("test-key");
        properties.setEnabled(true);

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-compression-contract");
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        when(tools.toolDefinitions(context)).thenReturn(RealtimeToolDefinitions.all());
        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context, mock(VoiceTransportSession.class), properties, tools,
                mock(CallTranscriptService.class), mock(CallSummaryService.class),
                mock(CallLifecycleService.class), mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(), HttpClient.newHttpClient());

        JSONObject setup = session.buildSetup().getJSONObject("setup");
        JSONObject compression = setup.getJSONObject("contextWindowCompression");
        assertEquals("25000", compression.getString("triggerTokens"));
        assertEquals("12000", compression.getJSONObject("slidingWindow").getString("targetTokens"));
        assertEquals("Sulafat", setup.getJSONObject("generationConfig")
                .getJSONObject("speechConfig").getJSONObject("voiceConfig")
                .getJSONObject("prebuiltVoiceConfig").getString("voiceName"));
        assertEquals("START_OF_ACTIVITY_INTERRUPTS",
                setup.getJSONObject("realtimeInputConfig").getString("activityHandling"));
        assertEquals("Reglas oficiales del negocio", setup.getJSONObject("systemInstruction")
                .getJSONArray("parts").getJSONObject(0).getString("text").contains("Reglas oficiales del negocio")
                ? "Reglas oficiales del negocio" : "missing");

        properties.setContextWindowCompressionTriggerTokens(1);
        properties.setContextWindowCompressionTargetTokens(1);
        compression = session.buildSetup().getJSONObject("setup").getJSONObject("contextWindowCompression");
        assertEquals("16000", compression.getString("triggerTokens"));
        assertEquals("4000", compression.getJSONObject("slidingWindow").getString("targetTokens"));

        properties.setContextWindowCompressionTriggerTokens(1_000_000);
        properties.setContextWindowCompressionTargetTokens(1_000_000);
        compression = session.buildSetup().getJSONObject("setup").getJSONObject("contextWindowCompression");
        assertEquals("64000", compression.getString("triggerTokens"));
        assertEquals("60000", compression.getJSONObject("slidingWindow").getString("targetTokens"));

        properties.setContextWindowCompressionEnabled(false);
        assertFalse(session.buildSetup().getJSONObject("setup").has("contextWindowCompression"));
    }

}
