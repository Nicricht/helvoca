package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiLiveVoiceSessionTest {

    @Test
    void setupUsesNativeAudioVoiceTranscriptsAndRecepVozTools() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setEnabled(true);
        properties.setApiKey("gemini-test-key");
        properties.setModel("gemini-3.1-flash-live-preview");
        properties.setVoice("Kore");

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-test");
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");

        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                mock(VoiceTransportSession.class),
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        JSONObject root = session.buildSetup();
        JSONObject setup = root.getJSONObject("setup");

        assertEquals("models/gemini-3.1-flash-live-preview", setup.getString("model"));
        JSONArray modalities = setup.getJSONObject("generationConfig").getJSONArray("responseModalities");
        assertEquals("AUDIO", modalities.getString(0));
        assertEquals("Kore", setup.getJSONObject("generationConfig")
                .getJSONObject("speechConfig")
                .getJSONObject("voiceConfig")
                .getJSONObject("prebuiltVoiceConfig")
                .getString("voiceName"));
        assertTrue(setup.has("inputAudioTranscription"));
        assertTrue(setup.has("outputAudioTranscription"));

        String instructions = setup.getJSONObject("systemInstruction")
                .getJSONArray("parts").getJSONObject(0).getString("text");
        assertTrue(instructions.contains("Reglas oficiales del negocio"));
        assertTrue(instructions.contains("RecepVoz"));
        assertTrue(instructions.contains("[RECEPVOZ_CALL_CONNECTED]"));

        JSONArray declarations = setup.getJSONArray("tools")
                .getJSONObject(0).getJSONArray("functionDeclarations");
        assertTrue(declarations.length() >= 10);
        assertTrue(hasFunction(declarations, "create_booking"));
        assertTrue(hasFunction(declarations, "list_available_slots"));
        assertTrue(hasFunction(declarations, "transfer_to_human"));
    }

    private static boolean hasFunction(JSONArray declarations, String name) {
        for (int i = 0; i < declarations.length(); i++) {
            if (name.equals(declarations.getJSONObject(i).optString("name"))) return true;
        }
        return false;
    }
}
