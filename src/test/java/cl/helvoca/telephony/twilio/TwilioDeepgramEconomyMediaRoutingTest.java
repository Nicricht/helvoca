package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.DeepgramEconomyVoiceProvider;
import cl.helvoca.voice.VoiceAiProviderRegistry;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TwilioDeepgramEconomyMediaRoutingTest {

    @Test
    void signedPinnedEconomyRouteStartsButUnconfiguredEconomyRouteIsDenied() throws Exception {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAuthToken("test-only-route-signature");
        TwilioMediaRouteSigner signer = new TwilioMediaRouteSigner(twilio);
        VoiceAiProviderRegistry registry = mock(VoiceAiProviderRegistry.class);
        DeepgramEconomyVoiceProvider economy = mock(DeepgramEconomyVoiceProvider.class);
        VoiceAiSession ai = mock(VoiceAiSession.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        TenantDatabaseContext db = new TenantDatabaseContext();
        TwilioCallBindingGuard binding = mock(TwilioCallBindingGuard.class);
        String business = "+56911112222";
        String caller = "+56944445555";
        String sid = "CA-economy-allow";
        String stream = "MZ-economy-allow";
        UUID callId = UUID.randomUUID();
        RealtimeCallContext call = new RealtimeCallContext(callId, UUID.randomUUID(),
                null, caller, business, stream);
        when(binding.allowsMedia(anyString(), eq(business), eq(caller), anyString()))
                .thenReturn(true);
        when(registry.require(DeepgramEconomyVoiceProvider.ID)).thenReturn(economy);
        when(economy.id()).thenReturn(DeepgramEconomyVoiceProvider.ID);
        when(economy.configured()).thenReturn(false); // Unscoped readiness must not authorize a call.
        when(economy.configuredFor(business)).thenReturn(true);
        when(economy.createSession(eq(call), any(VoiceTransportSession.class))).thenReturn(ai);
        when(lifecycle.startInboundCall("twilio", sid, caller, business)).thenReturn(callId);
        when(lifecycle.markStreamStarted(callId, sid, stream, DeepgramEconomyVoiceProvider.ID))
                .thenReturn(call);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("socket-economy-allow");
        when(socket.isOpen()).thenReturn(true);

        TwilioMediaStreamHandler handler = new TwilioMediaStreamHandler(
                signer, registry, health, lifecycle, control, db, binding);
        handler.afterConnectionEstablished(socket);
        handler.handleTextMessage(socket, signedStart(signer, business, caller, sid, stream));

        verify(economy).configuredFor(business);
        verify(economy, never()).configured();
        verify(economy).createSession(eq(call), any(VoiceTransportSession.class));
        verify(ai).start();
        assertEquals(TenantDatabaseContext.Mode.DENIED, db.currentOrDenied().mode());

        // Same valid signature and binding, but without provider authorization.
        when(economy.configuredFor(business)).thenReturn(false);
        String refusedSid = "CA-economy-deny";
        WebSocketSession denied = mock(WebSocketSession.class);
        when(denied.getId()).thenReturn("socket-economy-deny");
        handler.afterConnectionEstablished(denied);
        handler.handleTextMessage(denied,
                signedStart(signer, business, caller, refusedSid, "MZ-economy-deny"));
        verify(denied).close(CloseStatus.SERVICE_RESTARTED);
        verify(lifecycle, never()).startInboundCall("twilio", refusedSid, caller, business);
    }

    private static TextMessage signedStart(TwilioMediaRouteSigner signer, String business,
                                            String caller, String callSid, String streamSid) {
        long issuedAt = Instant.now().getEpochSecond();
        String signed = signer.sign(business, caller, callSid,
                DeepgramEconomyVoiceProvider.ID, issuedAt);
        JSONObject start = new JSONObject().put("event", "start")
                .put("streamSid", streamSid)
                .put("start", new JSONObject()
                        .put("streamSid", streamSid)
                        .put("callSid", callSid)
                        .put("accountSid", "AC-economy-test")
                        .put("customParameters", new JSONObject()
                                .put("business", business).put("caller", caller)
                                .put("callSid", callSid)
                                .put("provider", DeepgramEconomyVoiceProvider.ID)
                                .put("issuedAt", String.valueOf(issuedAt))
                                .put("route", signed)));
        return new TextMessage(start.toString());
    }
}
