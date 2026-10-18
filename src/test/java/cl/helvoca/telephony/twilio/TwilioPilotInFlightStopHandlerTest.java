package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.operations.ControlledPilotExternalEffectGuard;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiProviderRegistry;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwilioPilotInFlightStopHandlerTest {
    private static final String CALL = "CA" + "a".repeat(32);
    private static final String STREAM = "MZ" + "b".repeat(32);
    private static final String ACCOUNT = "AC" + "c".repeat(32);
    private static final String NUMBER = "+14355652512";
    private static final String CALLER = "+56911111111";
    private final UUID tenantId = UUID.randomUUID();
    private final UUID callId = UUID.randomUUID();

    private TwilioMediaRouteSigner signer;
    private VoiceAiProviderRegistry providers;
    private VoiceAiProvider provider;
    private VoiceAiSession ai;
    private CallLifecycleService lifecycle;
    private TwilioCallControl control;
    private TenantDatabaseContext db;
    private TwilioCallBindingGuard binding;
    private WebSocketSession socket;
    private ControlledPilotExternalEffectGuard pilotGuard;
    private ScheduledExecutorService deadlines;
    private ScheduledExecutorService checks;
    private ScheduledFuture<?> deadlineFuture;
    private ScheduledFuture<?> checkFuture;
    private Runnable checkTick;
    private Runnable deadlineTick;
    private TwilioMediaStreamHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        TwilioProperties props = new TwilioProperties();
        props.setAuthToken("local-test-only");
        signer = new TwilioMediaRouteSigner(props);
        providers = mock(VoiceAiProviderRegistry.class);
        provider = mock(VoiceAiProvider.class);
        ai = mock(VoiceAiSession.class);
        lifecycle = mock(CallLifecycleService.class);
        control = mock(TwilioCallControl.class);
        db = new TenantDatabaseContext();
        binding = mock(TwilioCallBindingGuard.class);
        socket = mock(WebSocketSession.class);
        pilotGuard = mock(ControlledPilotExternalEffectGuard.class);
        deadlines = mock(ScheduledExecutorService.class);
        checks = mock(ScheduledExecutorService.class);
        deadlineFuture = mock(ScheduledFuture.class);
        checkFuture = mock(ScheduledFuture.class);

        when(binding.allowsMedia(CALL, NUMBER, CALLER, ACCOUNT)).thenReturn(true);
        when(socket.getId()).thenReturn("pilot-socket");
        when(socket.isOpen()).thenReturn(true);
        when(providers.require("gemini")).thenReturn(provider);
        when(provider.id()).thenReturn("gemini");
        when(provider.configured()).thenReturn(true);
        when(lifecycle.startInboundCall("twilio", CALL, CALLER, NUMBER)).thenReturn(callId);
        when(lifecycle.markStreamStarted(callId, CALL, STREAM, "gemini"))
                .thenReturn(new RealtimeCallContext(callId, tenantId, null, CALLER, NUMBER, STREAM));
        when(provider.createSession(any(), any())).thenReturn(ai);
        when(deadlines.schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS)))
                .thenAnswer(invocation -> {
                    deadlineTick = invocation.getArgument(0);
                    return deadlineFuture;
                });
        when(checks.scheduleWithFixedDelay(any(Runnable.class), eq(2L), eq(2L), eq(TimeUnit.SECONDS)))
                .thenAnswer(invocation -> {
                    checkTick = invocation.getArgument(0);
                    return checkFuture;
                });
        when(control.hangup(ACCOUNT, CALL)).thenReturn(true);
        handler = handler();
    }

    private TwilioMediaStreamHandler handler() {
        return new TwilioMediaStreamHandler(signer, providers, new VoiceProviderHealthRegistry(),
                lifecycle, control, db, binding, new TwilioMediaSessionDeadline(30, deadlines),
                new TwilioPilotInFlightStopMonitor(pilotGuard, db, 2, checks));
    }

    private void validStart() throws Exception {
        long now = Instant.now().getEpochSecond();
        JSONObject start = new JSONObject().put("event", "start").put("streamSid", STREAM)
                .put("start", new JSONObject().put("streamSid", STREAM)
                        .put("accountSid", ACCOUNT).put("callSid", CALL)
                        .put("customParameters", new JSONObject()
                                .put("business", NUMBER).put("caller", CALLER)
                                .put("callSid", CALL).put("provider", "gemini")
                                .put("issuedAt", Long.toString(now))
                                .put("route", signer.sign(NUMBER, CALLER, CALL, "gemini", now))));
        handler.afterConnectionEstablished(socket);
        handler.handleTextMessage(socket, new TextMessage(start.toString()));
    }

    private void decision(boolean allowed) {
        when(pilotGuard.evaluate(tenantId, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(new ControlledPilotExternalEffectGuard.Decision(
                        allowed, true, allowed ? "ALLOWED" : "PILOT_NOT_RUNNING",
                        allowed ? "RUNNING" : "PAUSED",
                        ControlledPilotExternalEffectGuard.Effect.VOICE));
    }

    @Test
    void runningCallIsStoppedByPausingTenantEvenWithoutMoreAudio() throws Exception {
        decision(true);
        validStart();
        assertNotNull(checkTick);
        assertNotNull(deadlineTick);
        checkTick.run();
        verifyNoInteractions(control);
        decision(false);

        checkTick.run();
        verify(control).hangup(ACCOUNT, CALL);
        verify(socket).close(CloseStatus.SERVER_ERROR);
        verify(ai).close();
        verify(deadlineFuture).cancel(false);
        verify(checkFuture).cancel(false);
        verify(lifecycle).markStreamStopped(STREAM);
        verify(lifecycle).updateStatus(callId, "failed", null);
        assertEquals(TenantDatabaseContext.Mode.DENIED, db.currentOrDenied().mode());

        handler.handleTextMessage(socket, new TextMessage(new JSONObject()
                .put("event", "media").put("streamSid", STREAM)
                .put("media", new JSONObject().put("payload", "late-audio")).toString()));
        verify(ai, never()).acceptInboundAudio(anyString());
        checkTick.run();
        deadlineTick.run();
        handler.afterConnectionClosed(socket, CloseStatus.NORMAL);
        verify(control, times(1)).hangup(ACCOUNT, CALL);
        verify(ai, times(1)).close();
        verify(lifecycle, times(1)).markStreamStopped(STREAM);
    }

    @Test
    void watchUsesTenantIdFromAuthenticatedCallNotSignedPhoneParameter() throws Exception {
        decision(true);
        validStart();
        checkTick.run();
        verify(pilotGuard).evaluate(tenantId, ControlledPilotExternalEffectGuard.Effect.VOICE);
        assertEquals(TenantDatabaseContext.Mode.DENIED, db.currentOrDenied().mode());
        verifyNoInteractions(control);
    }

    @Test
    void carrierStopBeforePauseWinsAndDoesNotInitiateSecondHangup() throws Exception {
        decision(false);
        validStart();
        handler.handleTextMessage(socket, new TextMessage(
                new JSONObject().put("event", "stop").put("streamSid", STREAM).toString()));
        checkTick.run();
        deadlineTick.run();
        verify(control, never()).hangup(anyString(), anyString());
        verify(checkFuture).cancel(false);
        verify(ai, times(1)).close();
        verify(lifecycle, times(1)).markStreamStopped(STREAM);
    }

    @Test
    void carrierHangupRejectionDoesNotLeaveLocalAiRunning() throws Exception {
        when(control.hangup(ACCOUNT, CALL)).thenReturn(false);
        decision(false);
        validStart();
        checkTick.run();
        verify(control).hangup(ACCOUNT, CALL);
        verify(socket).close(CloseStatus.SERVER_ERROR);
        verify(ai).close();
        verify(lifecycle).markStreamStopped(STREAM);
        assertEquals(TenantDatabaseContext.Mode.DENIED, db.currentOrDenied().mode());
    }

    @Test
    void carrierCallControlExceptionStillClosesSocketAndAi() throws Exception {
        when(control.hangup(ACCOUNT, CALL)).thenThrow(new IllegalStateException("provider down"));
        decision(false);
        validStart();
        checkTick.run();
        verify(control).hangup(ACCOUNT, CALL);
        verify(socket).close(CloseStatus.SERVER_ERROR);
        verify(ai).close();
        verify(lifecycle).updateStatus(callId, "failed", null);
    }

    @Test
    void socketCloseErrorOnCarrierStopStillCleansUpAi() throws Exception {
        when(control.hangup(ACCOUNT, CALL)).thenThrow(new IllegalStateException("provider down"));
        doThrow(new java.io.IOException("socket unavailable")).when(socket).close(CloseStatus.SERVER_ERROR);
        decision(false);
        validStart();
        checkTick.run();
        verify(control).hangup(ACCOUNT, CALL);
        verify(socket).close(CloseStatus.SERVER_ERROR);
        verify(ai).close();
        verify(checkFuture).cancel(false);
    }

    @Test
    void watcherSchedulingFailureFailsClosedDuringSessionStart() {
        when(checks.scheduleWithFixedDelay(any(Runnable.class), eq(2L), eq(2L), eq(TimeUnit.SECONDS)))
                .thenThrow(new RejectedExecutionException("watchdog unavailable"));
        assertThrows(RejectedExecutionException.class, this::validStart);
        verify(ai).start();
        verify(ai).close();
        verify(control).hangup(ACCOUNT, CALL);
        verify(deadlineFuture).cancel(false);
        verify(lifecycle).updateStatus(callId, "failed", null);
    }

    @Test
    void unstartedStreamFrameHelpersRefuseMediaAndPlaybackWithoutTransport() {
        // Normally requireStream rejects these frames first. Invoke the
        // private defensive handlers directly to exercise their own
        // !state.started guards, including a premature callback/race.
        handler.afterConnectionEstablished(socket);
        @SuppressWarnings("unchecked")
        Map<String, ?> states = (Map<String, ?>) ReflectionTestUtils.getField(handler, "states");
        assertNotNull(states);
        Object state = states.get("pilot-socket");
        assertNotNull(state);
        JSONObject audio = new JSONObject().put("media",
                new JSONObject().put("track", "inbound").put("payload", "unstarted"));
        JSONObject mark = new JSONObject().put("mark",
                new JSONObject().put("name", "unstarted"));

        ReflectionTestUtils.invokeMethod(handler, "handleMedia", state, audio);
        ReflectionTestUtils.invokeMethod(handler, "handleMark", state, mark);

        verifyNoInteractions(providers, lifecycle, control, ai, pilotGuard);
        assertEquals(TenantDatabaseContext.Mode.DENIED, db.currentOrDenied().mode());
    }

    @Test
    void audioAndPlaybackFramesCannotResumeAfterDeadlineHasClosedStream() throws Exception {
        decision(true);
        validStart();
        JSONObject media = new JSONObject().put("event", "media").put("streamSid", STREAM)
                .put("media", new JSONObject()
                        .put("track", "inbound").put("payload", "before-stop"));
        JSONObject mark = new JSONObject().put("event", "mark").put("streamSid", STREAM)
                .put("mark", new JSONObject().put("name", "observed-mark"));

        handler.handleTextMessage(socket, new TextMessage(media.toString()));
        handler.handleTextMessage(socket, new TextMessage(mark.toString()));
        verify(ai).acceptInboundAudio("before-stop");

        // Unlike the pilot STOP cleanup, expiry intentionally retains its
        // closed state until the carrier WebSocket disconnect callback.
        deadlineTick.run();
        handler.handleTextMessage(socket, new TextMessage(media.toString()));
        handler.handleTextMessage(socket, new TextMessage(mark.toString()));
        verify(ai, times(1)).acceptInboundAudio("before-stop");
        verify(ai).close();
        verify(control, times(1)).hangup(ACCOUNT, CALL);
        verify(checkFuture).cancel(false);
    }

    @Test
    void defensiveStartedStreamWithoutAiOrTransportDropsBothFrameTypes() throws Exception {
        decision(true);
        validStart();

        // Simulate transient/incomplete collaborator publication while the
        // authenticated stream state remains marked started and not closed.
        // This checks both null-object guard outcomes independently of STOP.
        @SuppressWarnings("unchecked")
        Map<String, ?> states = (Map<String, ?>) ReflectionTestUtils.getField(handler, "states");
        assertNotNull(states);
        Object state = states.get("pilot-socket");
        assertNotNull(state);
        ReflectionTestUtils.setField(state, "ai", null);
        ReflectionTestUtils.setField(state, "transport", null);

        handler.handleTextMessage(socket, new TextMessage(
                new JSONObject().put("event", "media").put("streamSid", STREAM)
                        .put("media", new JSONObject().put("payload", "no-provider")).toString()));
        handler.handleTextMessage(socket, new TextMessage(
                new JSONObject().put("event", "mark").put("streamSid", STREAM)
                        .put("mark", new JSONObject().put("name", "no-transport")).toString()));

        verify(ai, never()).acceptInboundAudio(anyString());
        verifyNoInteractions(control);
        assertEquals(TenantDatabaseContext.Mode.DENIED, db.currentOrDenied().mode());
    }

    @Test
    void synchronousStopDuringSchedulingCancelsReturnedWatchFuture() throws Exception {
        decision(false);
        when(checks.scheduleWithFixedDelay(any(Runnable.class), eq(2L), eq(2L), eq(TimeUnit.SECONDS)))
                .thenAnswer(invocation -> {
                    checkTick = invocation.getArgument(0);
                    checkTick.run();
                    return checkFuture;
                });
        validStart();
        verify(control).hangup(ACCOUNT, CALL);
        verify(ai).close();
        verify(checkFuture).cancel(false);
        verify(deadlineFuture).cancel(false);
    }
}
