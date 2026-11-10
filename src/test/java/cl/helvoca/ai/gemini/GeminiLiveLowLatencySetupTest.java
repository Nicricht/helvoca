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
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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


    @Test
    void compactToolDescriptionsPreserveCriticalBookingContractAndVoice() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        assertFalse(properties.isCompactToolDescriptionsEnabled());
        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-lean-tool-contract");
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Horarios oficiales, agenda real y política de reservas");
        when(tools.toolDefinitions(context)).thenReturn(RealtimeToolDefinitions.all());
        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context, mock(VoiceTransportSession.class), properties, tools,
                mock(CallTranscriptService.class), mock(CallSummaryService.class),
                mock(CallLifecycleService.class), mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(), HttpClient.newHttpClient());

        JSONObject ordinary = session.buildSetup().getJSONObject("setup");
        JSONArray ordinaryTools = ordinary.getJSONArray("tools").getJSONObject(0)
                .getJSONArray("functionDeclarations");
        properties.setCompactToolDescriptionsEnabled(true);
        assertTrue(properties.isCompactToolDescriptionsEnabled());
        JSONObject compact = session.buildSetup().getJSONObject("setup");
        JSONArray compactTools = compact.getJSONArray("tools").getJSONObject(0)
                .getJSONArray("functionDeclarations");
        JSONObject originalSizes = GeminiLiveVoiceSession.setupSizes(
                new JSONObject().put("setup", ordinary));
        JSONObject compactSizes = GeminiLiveVoiceSession.setupSizes(
                new JSONObject().put("setup", compact));

        assertEquals(ordinaryTools.length(), originalSizes.getInt("toolCount"));
        assertEquals(compactTools.length(), compactSizes.getInt("toolCount"));
        assertEquals(ordinaryTools.toString().length(),
                originalSizes.getInt("toolDeclarationsChars"));
        assertEquals(compactTools.toString().length(),
                compactSizes.getInt("toolDeclarationsChars"));
        assertEquals(ordinary.getJSONObject("systemInstruction").getJSONArray("parts")
                        .getJSONObject(0).getString("text").length(),
                originalSizes.getInt("systemInstructionChars"));
        assertEquals(originalSizes.getInt("systemInstructionChars"),
                compactSizes.getInt("systemInstructionChars"));
        assertTrue(originalSizes.getInt("toolDeclarationsChars")
                        - compactSizes.getInt("toolDeclarationsChars") >= 1500);
        assertTrue(originalSizes.getInt("messageChars")
                        > compactSizes.getInt("messageChars"));
        assertTrue(originalSizes.getInt("messageChars")
                        > originalSizes.getInt("toolDeclarationsChars"));

        assertEquals(ordinaryTools.length(), compactTools.length());
        assertTrue(ordinaryTools.toString().length() - compactTools.toString().length() >= 1500,
                "Live declarations should actually save at least 1500 characters");
        assertEquals(ordinary.getJSONObject("systemInstruction").toString(),
                compact.getJSONObject("systemInstruction").toString());
        assertEquals(ordinary.getJSONObject("generationConfig").toString(),
                compact.getJSONObject("generationConfig").toString());
        assertEquals("START_OF_ACTIVITY_INTERRUPTS",
                compact.getJSONObject("realtimeInputConfig").getString("activityHandling"));

        for (int i = 0; i < ordinaryTools.length(); i++) {
            JSONObject before = ordinaryTools.getJSONObject(i);
            JSONObject after = compactTools.getJSONObject(i);
            assertEquals(before.getString("name"), after.getString("name"));
            assertEquals(before.getString("behavior"), after.getString("behavior"));
            JSONObject originalSchema = before.getJSONObject("parameters");
            JSONObject compactSchema = after.getJSONObject("parameters");
            assertEquals(originalSchema.keySet(), compactSchema.keySet());
            assertEquals(originalSchema.getString("type"), compactSchema.getString("type"));
            JSONObject originalFields = originalSchema.getJSONObject("properties");
            JSONObject compactFields = compactSchema.getJSONObject("properties");
            assertEquals(originalFields.keySet(), compactFields.keySet());
            JSONArray originallyRequired = originalSchema.optJSONArray("required");
            JSONArray nowRequired = compactSchema.optJSONArray("required");
            assertEquals(originallyRequired == null ? null : originallyRequired.toString(),
                    nowRequired == null ? null : nowRequired.toString());
            for (String field : originalFields.keySet()) {
                JSONObject previousArgument = originalFields.getJSONObject(field);
                JSONObject currentArgument = compactFields.getJSONObject(field);
                assertEquals(previousArgument.keySet(), currentArgument.keySet());
                assertEquals(previousArgument.getString("type"), currentArgument.getString("type"));
            }
        }
        JSONObject availability = toolByName(compactTools, "list_available_slots");
        assertTrue(availability.getString("description").contains("nextAvailableSlots"));
        assertTrue(availability.getString("description").contains("customerSafeExplanation"));
        JSONObject booking = toolByName(compactTools, "create_booking");
        assertTrue(booking.getString("description").contains("FASE 1"));
        assertTrue(booking.getString("description").contains("FASE 2"));
        assertTrue(booking.getString("description").contains("bookingId"));
        assertTrue(booking.getJSONObject("parameters").getJSONObject("properties")
                .getJSONObject("requestedWeekScope").getString("description")
                .contains("NEXT_WEEK"));
        assertTrue(booking.getJSONObject("parameters").getJSONObject("properties")
                .getJSONObject("requestedWeekday").getString("description")
                .contains("dayOfWeek"));
        JSONObject cancellation = toolByName(compactTools, "cancel_booking");
        assertTrue(cancellation.getString("description").contains("solo cuando el cliente"));
        assertTrue(cancellation.getString("description").contains("success=true"));
        // No change to the end-call contract or an unrelated tool.
        assertEquals(toolByName(ordinaryTools, "end_call").getString("description"),
                toolByName(compactTools, "end_call").getString("description"));
        assertEquals(toolByName(ordinaryTools, "send_whatsapp_operation").toString().length(),
                toolByName(compactTools, "send_whatsapp_operation").toString().length());

        properties.setCompactToolDescriptionsEnabled(false);
        assertEquals(ordinaryTools.toString().length(), session.buildSetup().getJSONObject("setup")
                .getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations")
                .toString().length());
    }

    private static JSONObject toolByName(JSONArray tools, String name) {
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.getJSONObject(i);
            if (name.equals(tool.getString("name"))) return tool;
        }
        throw new AssertionError("Missing tool " + name);
    }

}
