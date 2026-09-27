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
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VoiceCommercialLifecycleCertificationTest {

    @Test
    void greetingContractStartsWithSulafatChileanFemaleIdentity() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        when(tools.toolDefinitions(context)).thenReturn(RealtimeToolDefinitions.all());

        GeminiLiveVoiceSession session = session(
                context, properties, tools,
                mock(VoiceTransportSession.class),
                mock(CallTranscriptService.class));

        JSONObject setup = session.buildSetup().getJSONObject("setup");
        assertEquals("Sulafat", setup.getJSONObject("generationConfig")
                .getJSONObject("speechConfig")
                .getJSONObject("voiceConfig")
                .getJSONObject("prebuiltVoiceConfig")
                .getString("voiceName"));
        assertEquals("es-CL", setup.getJSONObject("inputAudioTranscription")
                .getJSONArray("languageCodes").getString(0));

        String instructions = setup.getJSONObject("systemInstruction")
                .getJSONArray("parts").getJSONObject(0).getString("text");
        assertTrue(instructions.contains("voz claramente femenina, joven-adulta y luminosa"));
        assertTrue(instructions.contains("DESDE LA PRIMERA SÍLABA"));
        assertTrue(instructions.contains("CERO CALL CENTER"));

        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(socket));
        session.onOpen(socket);
        session.onText(socket, new JSONObject().put("setupComplete", new JSONObject()).toString(), true);

        verify(socket, atLeastOnce()).sendText(argThat(payload -> {
            JSONObject event = new JSONObject(payload.toString());
            JSONObject realtime = event.optJSONObject("realtimeInput");
            return realtime != null
                    && "[RECEPVOZ_CALL_CONNECTED]".equals(realtime.optString("text"));
        }), eq(true));
    }

    @Test
    void normalQuestionIsCapturedWithoutAccidentalHangup() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        CallTranscriptService transcripts = mock(CallTranscriptService.class);
        GeminiLiveVoiceSession session = session(context, properties, tools, transport, transcripts);

        WebSocket socket = mock(WebSocket.class);
        JSONObject question = new JSONObject()
                .put("serverContent", new JSONObject()
                        .put("inputTranscription", new JSONObject()
                                .put("text", "¿Qué servicios tienen?"))
                        .put("turnComplete", true));

        session.onText(socket, question.toString(), true);

        verify(transcripts).append(context.callId(), "USER", "¿Qué servicios tienen?");
        verify(tools, never()).prepareDeferredEndCall(any());
        verify(transport, never()).endAfterPlayback();
    }

    @Test
    void providerInterruptionClearsPlaybackAndSilenceIsNotClosingIntent() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        GeminiLiveVoiceSession session = session(
                context, properties, tools, transport, mock(CallTranscriptService.class));

        WebSocket socket = mock(WebSocket.class);
        session.onText(socket, new JSONObject()
                .put("serverContent", new JSONObject().put("interrupted", true))
                .toString(), true);

        verify(transport).clearPlayback(context.streamSid());
        assertTrue(!GeminiLiveVoiceSession.hasExplicitClosingIntent(""));
        assertTrue(!GeminiLiveVoiceSession.hasExplicitClosingIntent("   "));
    }

    private static GeminiLiveVoiceSession session(RealtimeCallContext context,
                                                   GeminiLiveProperties properties,
                                                   RealtimeToolService tools,
                                                   VoiceTransportSession transport,
                                                   CallTranscriptService transcripts) {
        return new GeminiLiveVoiceSession(
                context,
                transport,
                properties,
                tools,
                transcripts,
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());
    }

    private static RealtimeCallContext context() {
        return new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "caller-test", "business-test", "stream-test");
    }
}
