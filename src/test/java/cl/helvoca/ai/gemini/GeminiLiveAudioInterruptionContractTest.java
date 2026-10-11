package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
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
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiLiveAudioInterruptionContractTest {
    @Test
    void interruptedProviderTurnDropsBufferedAudioAndDoesNotPlayContradictoryParts() {
        RealtimeCallContext context = context();
        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        GeminiLiveVoiceSession session = session(context, transport);
        WebSocket socket = mock(WebSocket.class);

        session.onText(socket, assistantAudio(240, (short) 9000).toString(), true);
        verify(transport, never()).sendAudio(anyString(), anyString());

        // Gemini can send an interruption with stale modelTurn data in the
        // same message. Old audio must not be played after the clear.
        JSONObject interrupted = assistantAudio(480, (short) 9000)
                .getJSONObject("serverContent").put("interrupted", true);
        session.onText(socket, new JSONObject().put("serverContent", interrupted).toString(), true);
        verify(transport, times(1)).clearPlayback(context.streamSid());
        verify(transport, never()).sendAudio(anyString(), anyString());

        session.onText(socket, assistantAudio(480, (short) -9000).toString(), true);
        session.onText(socket, new JSONObject().put("serverContent", new JSONObject()
                .put("generationComplete", true)).toString(), true);
        session.onText(socket, new JSONObject().put("serverContent", new JSONObject()
                .put("turnComplete", true)).toString(), true);

        ArgumentCaptor<String> samples = ArgumentCaptor.forClass(String.class);
        verify(transport, times(1)).sendAudio(eq(context.streamSid()), samples.capture());
        assertEquals(160, Base64.getDecoder().decode(samples.getValue()).length);
        assertTrue(PcmuAudioCodec.decodeMulaw(Base64.getDecoder().decode(samples.getValue())[0]) < 0);
    }

    @Test
    void generationCompleteFlushesShortFinalAudioExactlyOnceBeforeTurnComplete() {
        RealtimeCallContext context = context();
        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        GeminiLiveVoiceSession session = session(context, transport);
        WebSocket socket = mock(WebSocket.class);

        session.onText(socket, assistantAudio(240, (short) 7500).toString(), true);
        verify(transport, never()).sendAudio(anyString(), anyString());

        session.onText(socket, new JSONObject().put("serverContent", new JSONObject()
                .put("generationComplete", true)).toString(), true);
        session.onText(socket, new JSONObject().put("serverContent", new JSONObject()
                .put("turnComplete", true)).toString(), true);

        ArgumentCaptor<String> audio = ArgumentCaptor.forClass(String.class);
        verify(transport, times(1)).sendAudio(eq(context.streamSid()), audio.capture());
        assertEquals(80, Base64.getDecoder().decode(audio.getValue()).length);
        assertTrue(PcmuAudioCodec.decodeMulaw(Base64.getDecoder().decode(audio.getValue())[0]) > 0);
    }

    @Test
    void gemini38UsesBlockingToolsToProtectBookingConfirmationOrdering() {
        RealtimeCallContext context = context();
        GeminiLiveProperties properties = new GeminiLiveProperties();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.toolDefinitions(context)).thenReturn(new JSONArray()
                .put(new JSONObject()
                        .put("name", "create_booking")
                        .put("description", "Create a booking")
                        .put("parameters", new JSONObject().put("type", "object")))
                .put(new JSONObject()
                        .put("name", "end_call")
                        .put("description", "Finish")
                        .put("parameters", new JSONObject().put("type", "object"))));
        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context, mock(VoiceTransportSession.class), properties,
                tools, mock(CallTranscriptService.class), mock(CallSummaryService.class),
                mock(CallLifecycleService.class), mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(), HttpClient.newHttpClient());

        assertEquals("gemini-3.8-live", properties.getModel());
        JSONArray declarations = session.buildSetup().getJSONObject("setup")
                .getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations");
        assertEquals(2, declarations.length());
        for (int i = 0; i < declarations.length(); i++) {
            assertEquals("BLOCKING", declarations.getJSONObject(i).getString("behavior"));
        }
    }

    private static GeminiLiveVoiceSession session(RealtimeCallContext context,
                                                    VoiceTransportSession transport) {
        return new GeminiLiveVoiceSession(context, transport, new GeminiLiveProperties(),
                mock(RealtimeToolService.class), mock(CallTranscriptService.class),
                mock(CallSummaryService.class), mock(CallLifecycleService.class),
                mock(CallCertificationService.class), new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());
    }

    private static RealtimeCallContext context() {
        return new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56912345678", "+14355652512", "MZ-part4-test", null);
    }

    private static JSONObject assistantAudio(int sampleCount, short level) {
        ByteBuffer buffer = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) buffer.putShort(level);
        return new JSONObject().put("serverContent", new JSONObject()
                .put("modelTurn", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("inlineData", new JSONObject()
                                        .put("mimeType", "audio/pcm;rate=24000")
                                        .put("data", Base64.getEncoder().encodeToString(buffer.array())))))));
    }
}
