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
        assertEquals(300, activity.getInt("silenceDurationMs"));
        assertEquals("START_OF_ACTIVITY_INTERRUPTS", realtimeInput.getString("activityHandling"));
        assertFalse(setup.has("proactivity"));
    }
}
