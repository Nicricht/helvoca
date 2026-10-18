package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceAiProvider;
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

/**
 * Offline failure injection at the Twilio Media WebSocket -> AI boundary.
 * No Twilio client, Google socket, Railway or phone call is used.
 */
class TwilioMediaStreamStartupRecoveryPhase7Test {
    private static final String BUSINESS = "+15551230001";
    private static final String CALLER = "+15551230002";
    private static final String ACCOUNT = "AC00000000000000000000000000000011";
    private static final String CALL = "CA00000000000000000000000000000022";
    private static final String STREAM = "MZ00000000000000000000000000000033";

    @Test
    void providerSessionCreationFailureClosesCarrierAndReleasesTrackedCallAndStream() throws Exception {
        Scenario s = new Scenario();
        when(s.provider.createSession(any(), any()))
                .thenThrow(new IllegalStateException("upstream returned private-token-MUST-NOT-LEAK"));

        assertThrows(IllegalStateException.class, s::start);

        s.assertFailClosed();
        verify(s.provider).createSession(eq(s.context), any(VoiceTransportSession.class));
        assertFalse(s.health.snapshot("gemini", true).detail().contains("private-token-MUST-NOT-LEAK"));
    }

    @Test
    void aiStartExceptionClosesCreatedSessionAndCarrierWithoutExposingSystemScope() throws Exception {
        Scenario s = new Scenario();
        VoiceAiSession ai = mock(VoiceAiSession.class);
        when(s.provider.createSession(eq(s.context), any(VoiceTransportSession.class))).thenReturn(ai);
        doThrow(new IllegalStateException("Gemini session failed")).when(ai).start();

        assertThrows(IllegalStateException.class, s::start);

        s.assertFailClosed();
        verify(ai).close();
        assertEquals("OPEN", s.health.snapshot("gemini", true).state());
        s.handler.handleTextMessage(s.socket, new TextMessage(new JSONObject()
                .put("event", "media").put("streamSid", STREAM)
                .put("media", new JSONObject().put("track", "inbound").put("payload", "AAAA"))
                .toString()));
        verify(ai, never()).acceptInboundAudio(anyString());
    }

    @Test
    void streamPersistenceFailureBeforeProviderCreationReleasesCallAndCarrier() throws Exception {
        Scenario s = new Scenario();
        when(s.lifecycle.markStreamStarted(s.callId, CALL, STREAM, "gemini"))
                .thenThrow(new IllegalStateException("PostgreSQL unavailable"));

        assertThrows(IllegalStateException.class, s::start);

        s.assertFailClosed();
        verify(s.provider, never()).createSession(any(), any());
        verify(s.lifecycle, never()).markAiModel(any(), any());
    }

    private static final class Scenario {
        private final TwilioProperties properties = new TwilioProperties();
        private final TwilioMediaRouteSigner signer;
        private final VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        private final VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        private final CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        private final TwilioCallControl control = mock(TwilioCallControl.class);
        private final TenantDatabaseContext databaseContext = new TenantDatabaseContext();
        private final TwilioCallBindingGuard guard = mock(TwilioCallBindingGuard.class);
        private final VoiceAiProvider provider = mock(VoiceAiProvider.class);
        private final WebSocketSession socket = mock(WebSocketSession.class);
        private final UUID callId = UUID.randomUUID();
        private final RealtimeCallContext context =
                new RealtimeCallContext(callId, UUID.randomUUID(), null, CALLER, BUSINESS, STREAM);
        private final TwilioMediaStreamHandler handler;
        private final long issuedAt = Instant.now().getEpochSecond();

        Scenario() {
            properties.setAuthToken("synthetic-phase7-route-key");
            signer = new TwilioMediaRouteSigner(properties);
            when(socket.getId()).thenReturn("socket-phase7-" + callId);
            when(socket.isOpen()).thenReturn(true);
            when(guard.allowsMedia(CALL, BUSINESS, CALLER, ACCOUNT)).thenReturn(true);
            when(providers.require("gemini")).thenReturn(provider);
            when(provider.id()).thenReturn("gemini");
            when(provider.configured()).thenReturn(true);
            when(lifecycle.startInboundCall("twilio", CALL, CALLER, BUSINESS)).thenReturn(callId);
            when(lifecycle.markStreamStarted(callId, CALL, STREAM, "gemini")).thenReturn(context);
            handler = new TwilioMediaStreamHandler(
                    signer, providers, health, lifecycle, control, databaseContext, guard);
            handler.afterConnectionEstablished(socket);
        }

        void start() throws Exception {
            String route = signer.sign(BUSINESS, CALLER, CALL, "gemini", issuedAt);
            JSONObject event = new JSONObject()
                    .put("event", "start")
                    .put("streamSid", STREAM)
                    .put("start", new JSONObject()
                            .put("streamSid", STREAM)
                            .put("accountSid", ACCOUNT)
                            .put("callSid", CALL)
                            .put("customParameters", new JSONObject()
                                    .put("business", BUSINESS)
                                    .put("caller", CALLER)
                                    .put("callSid", CALL)
                                    .put("provider", "gemini")
                                    .put("issuedAt", Long.toString(issuedAt))
                                    .put("route", route)));
            handler.handleTextMessage(socket, new TextMessage(event.toString()));
        }

        void assertFailClosed() throws Exception {
            verify(lifecycle).updateStatus(callId, "failed", null);
            verify(lifecycle).markStreamStopped(STREAM);
            verify(control).hangup(ACCOUNT, CALL);
            verify(socket).close(CloseStatus.SERVER_ERROR);
            assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
            assertFalse(health.allow("gemini", true));
        }
    }
}
