package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiProviderRegistry;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class TwilioMediaStreamHandlerTest {

    @Test
    void signedStartCreatesProviderSessionAndForwardsInboundAudio() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken("twilio-test-secret");
        TwilioMediaRouteSigner signer = new TwilioMediaRouteSigner(properties);
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        VoiceAiProvider provider = mock(VoiceAiProvider.class);
        VoiceAiSession ai = mock(VoiceAiSession.class);
        WebSocketSession socket = mock(WebSocketSession.class);

        String streamSid = "MZ-test-stream";
        String callSid = "CA-test-call";
        String accountSid = "AC-test-account";
        String business = "+14355652512";
        String caller = "+56911111111";
        long issuedAt = Instant.now().getEpochSecond();
        String route = signer.sign(business, caller, callSid, "gemini", issuedAt);
        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, caller, business, streamSid);

        when(socket.getId()).thenReturn("socket-1");
        when(socket.isOpen()).thenReturn(true);
        when(providers.require("gemini")).thenReturn(provider);
        when(provider.id()).thenReturn("gemini");
        when(provider.configured()).thenReturn(true);
        when(lifecycle.startInboundCall("twilio", callSid, caller, business)).thenReturn(callId);
        when(lifecycle.markStreamStarted(callId, callSid, streamSid, "gemini")).thenReturn(context);
        when(provider.createSession(eq(context), any(VoiceTransportSession.class))).thenReturn(ai);

        TwilioMediaStreamHandler handler = new TwilioMediaStreamHandler(
                signer, providers, health, lifecycle, control);
        handler.afterConnectionEstablished(socket);

        JSONObject start = new JSONObject()
                .put("event", "start")
                .put("streamSid", streamSid)
                .put("start", new JSONObject()
                        .put("streamSid", streamSid)
                        .put("accountSid", accountSid)
                        .put("callSid", callSid)
                        .put("customParameters", new JSONObject()
                                .put("business", business)
                                .put("caller", caller)
                                .put("callSid", callSid)
                                .put("provider", "gemini")
                                .put("issuedAt", String.valueOf(issuedAt))
                                .put("route", route)));
        handler.handleTextMessage(socket, new TextMessage(start.toString()));

        JSONObject media = new JSONObject()
                .put("event", "media")
                .put("streamSid", streamSid)
                .put("media", new JSONObject()
                        .put("track", "inbound")
                        .put("payload", "/////w=="));
        handler.handleTextMessage(socket, new TextMessage(media.toString()));

        verify(ai).start();
        verify(ai).acceptInboundAudio("/////w==");
        verify(lifecycle).startInboundCall("twilio", callSid, caller, business);
        verify(lifecycle).markStreamStarted(callId, callSid, streamSid, "gemini");

        ArgumentCaptor<VoiceTransportSession> transport = ArgumentCaptor.forClass(VoiceTransportSession.class);
        verify(provider).createSession(eq(context), transport.capture());
        assertNotNull(transport.getValue());
        assertEquals(streamSid, transport.getValue().id());
    }
}
