package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiProviderRegistry;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwilioMediaSessionDeadlineHandlerTest {
    private static final String CALL = "CA" + "a".repeat(32);
    private static final String STREAM = "MZ" + "b".repeat(32);
    private static final String ACCOUNT = "AC" + "c".repeat(32);
    private static final String NUMBER = "+14355652512";
    private static final String CALLER = "+56911111111";

    private TwilioMediaRouteSigner signer;
    private VoiceAiProviderRegistry providers;
    private VoiceProviderHealthRegistry health;
    private CallLifecycleService lifecycle;
    private TwilioCallControl control;
    private TenantDatabaseContext databaseContext;
    private TwilioCallBindingGuard binding;
    private VoiceAiProvider provider;
    private VoiceAiSession ai;
    private WebSocketSession socket;
    private TwilioMediaStreamHandler handler;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> future;
    private UUID callId;
    private Runnable expiry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        TwilioProperties props = new TwilioProperties();
        props.setAuthToken("local-test-only");
        signer = new TwilioMediaRouteSigner(props);
        providers = mock(VoiceAiProviderRegistry.class);
        health = new VoiceProviderHealthRegistry();
        lifecycle = mock(CallLifecycleService.class);
        control = mock(TwilioCallControl.class);
        databaseContext = new TenantDatabaseContext();
        binding = mock(TwilioCallBindingGuard.class);
        provider = mock(VoiceAiProvider.class);
        ai = mock(VoiceAiSession.class);
        socket = mock(WebSocketSession.class);
        scheduler = mock(ScheduledExecutorService.class);
        future = mock(ScheduledFuture.class);
        callId = UUID.randomUUID();
        when(binding.allowsMedia(CALL, NUMBER, CALLER, ACCOUNT)).thenReturn(true);
        when(socket.getId()).thenReturn("socket-deadline");
        when(socket.isOpen()).thenReturn(true);
        when(providers.require("gemini")).thenReturn(provider);
        when(provider.id()).thenReturn("gemini");
        when(provider.configured()).thenReturn(true);
        when(provider.modelId()).thenReturn("synthetic-test-model");
        when(lifecycle.startInboundCall("twilio", CALL, CALLER, NUMBER)).thenReturn(callId);
        when(lifecycle.markStreamStarted(callId, CALL, STREAM, "gemini"))
                .thenReturn(new RealtimeCallContext(callId, UUID.randomUUID(), null,
                        CALLER, NUMBER, STREAM));
        when(provider.createSession(any(RealtimeCallContext.class), any()))
                .thenReturn(ai);
        when(control.hangup(ACCOUNT, CALL)).thenReturn(true);
        when(scheduler.schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS)))
                .thenAnswer(invocation -> {
                    expiry = invocation.getArgument(0);
                    return future;
                });
        handler = new TwilioMediaStreamHandler(signer, providers, health, lifecycle, control,
                databaseContext, binding, new TwilioMediaSessionDeadline(30, scheduler));
    }

    private void validStart() throws Exception {
        long issuedAt = Instant.now().getEpochSecond();
        String sig = signer.sign(NUMBER, CALLER, CALL, "gemini", issuedAt);
        JSONObject start = new JSONObject()
                .put("event", "start")
                .put("streamSid", STREAM)
                .put("start", new JSONObject()
                        .put("streamSid", STREAM)
                        .put("accountSid", ACCOUNT)
                        .put("callSid", CALL)
                        .put("customParameters", new JSONObject()
                                .put("business", NUMBER)
                                .put("caller", CALLER)
                                .put("callSid", CALL)
                                .put("provider", "gemini")
                                .put("issuedAt", Long.toString(issuedAt))
                                .put("route", sig)));
        handler.afterConnectionEstablished(socket);
        handler.handleTextMessage(socket, new TextMessage(start.toString()));
    }

    @Test
    void acceptedSignedCallSchedulesRealCutoffAndKillsCarrierAndAiOnce() throws Exception {
        validStart();
        assertNotNull(expiry);
        verify(scheduler).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
        verify(ai).start();

        expiry.run();
        verify(control).hangup(ACCOUNT, CALL);
        verify(socket).close(CloseStatus.SERVER_ERROR);
        verify(ai).close();
        verify(lifecycle).markStreamStopped(STREAM);
        verify(lifecycle).updateStatus(callId, "failed", null);
        verify(future).cancel(false);
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());

        expiry.run();
        handler.afterConnectionClosed(socket, CloseStatus.NORMAL);
        verify(control, times(1)).hangup(ACCOUNT, CALL);
        verify(ai, times(1)).close();
        verify(lifecycle, times(1)).markStreamStopped(STREAM);
    }

    @Test
    void normalCarrierDisconnectCancelsExpiryAndNeverAttemptsHangup() throws Exception {
        validStart();
        handler.afterConnectionClosed(socket, CloseStatus.NORMAL);
        verify(future).cancel(false);
        verify(ai).close();
        expiry.run();
        verifyNoInteractions(control);
        verify(lifecycle, never()).updateStatus(callId, "failed", null);
    }

    @Test
    void carrierHangupFailureStillClosesLocalAiAndMarksFailure() throws Exception {
        when(control.hangup(ACCOUNT, CALL)).thenReturn(false);
        validStart();
        expiry.run();
        verify(control).hangup(ACCOUNT, CALL);
        verify(socket).close(CloseStatus.SERVER_ERROR);
        verify(ai).close();
        verify(lifecycle).updateStatus(callId, "failed", null);
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
    }

    @Test
    void unboundMediaCannotArmASpendTimer() throws Exception {
        when(binding.allowsMedia(CALL, NUMBER, CALLER, ACCOUNT)).thenReturn(false);
        validStart();
        assertNull(expiry);
        verifyNoInteractions(scheduler, control);
        verify(provider, never()).createSession(any(), any());
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void deadlineSchedulingFailureFailsClosedInsteadOfLeavingLiveAi() throws Exception {
        when(scheduler.schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS)))
                .thenThrow(new java.util.concurrent.RejectedExecutionException("unavailable"));
        assertThrows(java.util.concurrent.RejectedExecutionException.class, this::validStart);
        verify(ai).start();
        verify(ai).close();
        verify(control).hangup(ACCOUNT, CALL);
        verify(lifecycle).updateStatus(callId, "failed", null);
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
    }
}
