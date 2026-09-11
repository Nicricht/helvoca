package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiProviderRegistry;
import cl.helvoca.voice.VoiceAiSession;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TwilioMediaStreamHandlerTest {

    @Test
    void validPcmuStreamStartsAiAndPersistsStreamClose() throws Exception {
        TwilioCallService calls = mock(TwilioCallService.class);
        VoiceAiProviderRegistry registry = mock(VoiceAiProviderRegistry.class);
        TwilioCallControl callControl = mock(TwilioCallControl.class);
        VoiceAiProvider provider = mock(VoiceAiProvider.class);
        VoiceAiSession ai = mock(VoiceAiSession.class);
        WebSocketSession socket = mock(WebSocketSession.class);

        UUID callId = UUID.randomUUID();
        String callSid = "CA" + "b".repeat(32);
        String accountSid = "AC" + "a".repeat(32);
        String streamSid = "MZ" + "c".repeat(32);
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, "+56911111111", "+17372508034", streamSid);

        when(socket.getId()).thenReturn("socket-1");
        when(registry.active()).thenReturn(provider);
        when(provider.configured()).thenReturn(true);
        when(provider.id()).thenReturn("openai");
        when(calls.markStreamStarted(callId, callSid, streamSid, "openai")).thenReturn(context);
        when(provider.createSession(eq(context), any())).thenReturn(ai);

        TwilioMediaStreamHandler handler = new TwilioMediaStreamHandler(calls, registry, callControl);
        handler.handleTextMessage(socket, new TextMessage(startMessage(callId, accountSid, callSid, streamSid,
                "audio/x-mulaw", 8000, 1).toString()));

        verify(ai).start();
        verify(calls).markStreamStarted(callId, callSid, streamSid, "openai");

        handler.afterConnectionClosed(socket, CloseStatus.NORMAL);
        verify(ai).close();
        verify(calls).markStreamStopped(streamSid);
    }

    @Test
    void unsupportedAudioFormatIsRejectedBeforeAiStarts() throws Exception {
        TwilioCallService calls = mock(TwilioCallService.class);
        VoiceAiProviderRegistry registry = mock(VoiceAiProviderRegistry.class);
        TwilioCallControl callControl = mock(TwilioCallControl.class);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("socket-2");

        UUID callId = UUID.randomUUID();
        String callSid = "CA" + "b".repeat(32);
        String accountSid = "AC" + "a".repeat(32);
        String streamSid = "MZ" + "c".repeat(32);

        TwilioMediaStreamHandler handler = new TwilioMediaStreamHandler(calls, registry, callControl);
        handler.handleTextMessage(socket, new TextMessage(startMessage(callId, accountSid, callSid, streamSid,
                "audio/pcm", 24000, 1).toString()));

        verify(socket).close(CloseStatus.BAD_DATA);
        verifyNoInteractions(registry);
        verify(calls, never()).markStreamStarted(any(), anyString(), anyString(), anyString());
    }

    private static JSONObject startMessage(UUID callId,
                                           String accountSid,
                                           String callSid,
                                           String streamSid,
                                           String encoding,
                                           int sampleRate,
                                           int channels) {
        return new JSONObject()
                .put("event", "start")
                .put("streamSid", streamSid)
                .put("start", new JSONObject()
                        .put("accountSid", accountSid)
                        .put("callSid", callSid)
                        .put("streamSid", streamSid)
                        .put("customParameters", new JSONObject().put("callId", callId.toString()))
                        .put("mediaFormat", new JSONObject()
                                .put("encoding", encoding)
                                .put("sampleRate", sampleRate)
                                .put("channels", channels)));
    }
}
