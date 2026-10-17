package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.gemini.VoiceBakeOffCatalog;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiProviderRegistry;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TwilioMediaStreamHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(TwilioMediaStreamHandler.class);

    private final TwilioMediaRouteSigner routeSigner;
    private final VoiceAiProviderRegistry providers;
    private final VoiceProviderHealthRegistry health;
    private final CallLifecycleService lifecycle;
    private final TwilioCallControl callControl;
    private final TenantDatabaseContext databaseContext;
    private final TwilioCallBindingGuard bindingGuard;
    private final TwilioMediaSessionDeadline deadline;
    private final TwilioPilotInFlightStopMonitor pilotStopMonitor;
    private final ConcurrentMap<String, StreamState> states = new ConcurrentHashMap<>();

    @Autowired
    public TwilioMediaStreamHandler(TwilioMediaRouteSigner routeSigner,
                                    VoiceAiProviderRegistry providers,
                                    VoiceProviderHealthRegistry health,
                                    CallLifecycleService lifecycle,
                                    TwilioCallControl callControl,
                                    TenantDatabaseContext databaseContext,
                                    TwilioCallBindingGuard bindingGuard,
                                    TwilioMediaSessionDeadline deadline,
                                    TwilioPilotInFlightStopMonitor pilotStopMonitor) {
        this.routeSigner = routeSigner;
        this.providers = providers;
        this.health = health;
        this.lifecycle = lifecycle;
        this.callControl = callControl;
        this.databaseContext = databaseContext;
        this.bindingGuard = bindingGuard;
        this.deadline = deadline;
        this.pilotStopMonitor = pilotStopMonitor;
    }

    // Legacy unit tests construct the handler without a production watcher.
    TwilioMediaStreamHandler(TwilioMediaRouteSigner routeSigner,
                             VoiceAiProviderRegistry providers,
                             VoiceProviderHealthRegistry health,
                             CallLifecycleService lifecycle,
                             TwilioCallControl callControl,
                             TenantDatabaseContext databaseContext,
                             TwilioCallBindingGuard bindingGuard) {
        this(routeSigner, providers, health, lifecycle, callControl, databaseContext,
                bindingGuard, new TwilioMediaSessionDeadline(600), null);
    }

    TwilioMediaStreamHandler(TwilioMediaRouteSigner routeSigner,
                             VoiceAiProviderRegistry providers,
                             VoiceProviderHealthRegistry health,
                             CallLifecycleService lifecycle,
                             TwilioCallControl callControl,
                             TenantDatabaseContext databaseContext,
                             TwilioCallBindingGuard bindingGuard,
                             TwilioMediaSessionDeadline deadline) {
        this(routeSigner, providers, health, lifecycle, callControl, databaseContext,
                bindingGuard, deadline, null);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        states.put(session.getId(), new StreamState());
        log.info("Twilio media WebSocket connected socket={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JSONObject event;
        try {
            event = new JSONObject(message.getPayload());
        } catch (Exception e) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        StreamState state = states.computeIfAbsent(session.getId(), ignored -> new StreamState());
        switch (event.optString("event", "")) {
            case "connected" -> { }
            case "start" -> handleStart(session, state, event);
            case "media" -> { if (requireStream(session, state, event)) handleMedia(state, event); }
            case "mark" -> { if (requireStream(session, state, event)) handleMark(state, event); }
            case "stop" -> { if (requireStream(session, state, event)) closeState(state, false); }
            case "dtmf" -> { }
            default -> log.debug("Ignoring Twilio media event={} socket={}",
                    event.optString("event", ""), session.getId());
        }
    }

    private boolean requireStream(WebSocketSession socket, StreamState state, JSONObject event) throws Exception {
        if (!state.started) return false;
        if (TwilioMediaStreamEventBinding.matchesStream(state.streamSid, event)) return true;
        // Never forward a second stream's audio, playback acknowledgement or stop event.
        log.warn("Rejected cross-stream Twilio media event socket={}", socket.getId());
        closeState(state, true);
        socket.close(CloseStatus.POLICY_VIOLATION);
        return false;
    }

    private void handleStart(WebSocketSession socket, StreamState state, JSONObject event) throws Exception {
        if (state.started) {
            socket.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        JSONObject start = event.optJSONObject("start");
        if (start == null) {
            socket.close(CloseStatus.BAD_DATA);
            return;
        }
        if (!TwilioMediaStreamEventBinding.consistentStart(event, start)) {
            socket.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        JSONObject parameters = start.optJSONObject("customParameters");
        if (parameters == null) {
            socket.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String streamSid = first(event.optString("streamSid", null), start.optString("streamSid", null));
        String callSid = start.optString("callSid", null);
        String accountSid = start.optString("accountSid", null);
        String businessPhone = parameters.optString("business", null);
        String callerPhone = parameters.optString("caller", null);
        String routedCallSid = parameters.optString("callSid", null);
        String providerId = parameters.optString("provider", null);
        String voiceOverrideRaw = parameters.optString("voiceOverride", null);
        String voiceOverride = VoiceBakeOffCatalog.normalize(voiceOverrideRaw);
        String issuedAt = parameters.optString("issuedAt", null);
        String route = parameters.optString("route", null);

        boolean voiceOverrideValid = !notBlank(voiceOverrideRaw) || voiceOverride != null;
        boolean valid = notBlank(streamSid)
                && notBlank(callSid)
                && callSid.equals(routedCallSid)
                && voiceOverrideValid
                && routeSigner.verify(
                        businessPhone, callerPhone, callSid, providerId, voiceOverride, issuedAt, route);
        if (!valid) {
            log.warn("Rejected invalid Twilio media route socket={} call={}", socket.getId(), callSid);
            socket.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // A signed route alone is insufficient: the socket's untrusted
        // AccountSid and CallSid must identify the already admitted tenant call.
        if (!bindingGuard.allowsMedia(callSid, businessPhone, callerPhone, accountSid)) {
            log.warn("Rejected unbound Twilio media stream socket={}", socket.getId());
            socket.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        VoiceAiProvider provider;
        try {
            provider = providers.require(providerId);
        } catch (IllegalStateException e) {
            log.warn("Rejected unknown voice provider={} call={}", providerId, callSid);
            socket.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (!health.allow(provider.id(), provider.configured())) {
            log.warn("Voice provider circuit is open provider={} call={}", provider.id(), callSid);
            socket.close(CloseStatus.SERVICE_RESTARTED);
            return;
        }

        // WebSocket messages bypass the servlet tenant filter. Elevate only after
        // the media route signature is verified; each DB access restores its scope.
        UUID callId = databaseContext.callAsSystem(
                () -> lifecycle.startInboundCall("twilio", callSid, callerPhone, businessPhone));
        state.callId = callId;
        state.streamSid = streamSid;
        state.callSid = callSid;
        state.providerId = provider.id();
        TwilioVoiceTransportSession transport = new TwilioVoiceTransportSession(
                socket, streamSid, accountSid, callSid, callControl);
        state.transport = transport;
        try {
            RealtimeCallContext context = databaseContext.callAsSystem(
                    () -> lifecycle.markStreamStarted(callId, callSid, streamSid, provider.id()));
            databaseContext.runAsSystem(() -> lifecycle.markAiModel(callId, provider.modelId()));
            if (voiceOverride != null) {
                context = context.withVoiceOverride(voiceOverride);
                log.info("VOICE_BAKEOFF_ROUTE call={} voice={}", callId, voiceOverride);
            }
            if (provider.certificationSession(context)) {
                databaseContext.runAsSystem(() -> lifecycle.markCertification(callId));
                log.info("RECEPVOZ_CALL_CERTIFICATION armed call={} provider={}", callId, provider.id());
            }
            VoiceAiSession ai = provider.createSession(context, transport);
            state.ai = ai;
            state.started = true;
            ai.start();
            // Enforced on wall-clock even when the caller sends no media frames.
            // This must be scheduled only after an authenticated, bound start.
            state.deadlineTask = deadline.schedule(() -> expireSession(socket, state));
            if (state.closed.get()) state.deadlineTask.cancel(false);
            if (pilotStopMonitor != null) {
                // Reconcile PAUSE from PostgreSQL even when this instance has
                // no incoming frames or the operator used another replica.
                state.stopTask = pilotStopMonitor.watch(context.businessId(),
                        () -> stopPausedCall(socket, state));
                if (state.closed.get()) state.stopTask.cancel(false);
            }
        } catch (RuntimeException e) {
            // A failed provider/session initialization must not leave an active
            // call, an orphaned stream, or a live carrier connection.
            // Cleanup uses scoped SYSTEM operations and restores DENIED.
            health.failure(provider.id(), VoiceProviderHealthRegistry.FailureKind.UPSTREAM, null);
            closeState(state, true);
            transport.closeOnUpstreamFailure();
            throw e;
        }
        log.info("Twilio media stream started call={} stream={} provider={}", callSid, streamSid, provider.id());
    }

    private void handleMedia(StreamState state, JSONObject event) {
        if (!state.started || state.closed.get() || state.ai == null) return;
        JSONObject media = event.optJSONObject("media");
        if (media == null) return;
        String track = media.optString("track", "inbound");
        if (!"inbound".equalsIgnoreCase(track) && !"inbound_track".equalsIgnoreCase(track)) return;
        String payload = media.optString("payload", null);
        if (payload != null && !payload.isBlank()) state.ai.acceptInboundAudio(payload);
    }

    private void handleMark(StreamState state, JSONObject event) {
        if (!state.started || state.closed.get() || state.transport == null) return;
        JSONObject mark = event.optJSONObject("mark");
        if (mark == null) return;
        state.transport.onPlaybackMark(mark.optString("name", null));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        StreamState state = states.get(session.getId());
        if (state != null && state.callId != null) {
            try { databaseContext.runAsSystem(() -> lifecycle.updateStatus(state.callId, "failed", null)); }
            catch (Exception ignored) { }
            if (state.providerId != null) {
                health.failure(state.providerId, VoiceProviderHealthRegistry.FailureKind.UPSTREAM,
                        exception == null ? "Twilio media transport error" : exception.getMessage());
            }
        }
        if (state != null) closeState(state, true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        StreamState state = states.remove(session.getId());
        if (state != null) closeState(state, false);
        log.info("Twilio media WebSocket closed socket={} status={}", session.getId(), status);
    }

    private void stopPausedCall(WebSocketSession socket, StreamState state) {
        // Exactly one winner against disconnect, carrier STOP and hard deadline.
        if (!state.closed.compareAndSet(false, true)) return;
        log.warn("PILOT_INFLIGHT_VOICE_STOP_REQUESTED call={}", state.callSid);
        try {
            // Blocks further outgoing audio BEFORE trying the carrier hangup.
            ((TwilioVoiceTransportSession) state.transport).closeOnUpstreamFailure();
        } catch (RuntimeException e) {
            log.warn("PILOT_INFLIGHT_CARRIER_STOP_UNVERIFIED call={}", state.callSid);
            try {
                socket.close(CloseStatus.SERVER_ERROR);
            } catch (java.io.IOException ignored) {
                log.debug("Pilot STOP socket already unavailable call={}", state.callSid);
            }
        } finally {
            cleanupState(state, true);
            states.remove(socket.getId(), state);
        }
    }

    private void expireSession(WebSocketSession socket, StreamState state) {
        // A carrier stop or disconnect wins the race without any late hangup.
        if (!state.closed.compareAndSet(false, true)) return;
        log.warn("Twilio media session reached the configured wall-clock limit call={}", state.callSid);
        try {
            // A deadline is armed only after creating this verified Twilio
            // transport. A concurrent normal close wins the CAS above.
            ((TwilioVoiceTransportSession) state.transport).closeOnUpstreamFailure();
        } catch (RuntimeException ignored) {
            // A failed carrier API request must not leave the local socket and
            // AI alive. The timeout is a safety mechanism, not a cost receipt.
            try {
                socket.close(CloseStatus.SERVER_ERROR);
            } catch (java.io.IOException ignoredSocketError) {
                log.debug("Media socket already unavailable at deadline call={}", state.callSid);
            }
        } finally {
            cleanupState(state, true);
        }
    }

    private void closeState(StreamState state, boolean failed) {
        if (!state.closed.compareAndSet(false, true)) return;
        cleanupState(state, failed);
    }

    private void cleanupState(StreamState state, boolean failed) {
        ScheduledFuture<?> task = state.deadlineTask;
        if (task != null) task.cancel(false);
        ScheduledFuture<?> stopTask = state.stopTask;
        if (stopTask != null) stopTask.cancel(false);
        VoiceAiSession ai = state.ai;
        state.ai = null;
        state.transport = null;
        if (state.streamSid != null) {
            try { databaseContext.runAsSystem(() -> lifecycle.markStreamStopped(state.streamSid)); }
            catch (Exception ignored) { }
        }
        if (ai != null) {
            try { ai.close(); }
            catch (Exception ignored) { }
        }
        if (failed && state.callId != null) {
            try { databaseContext.runAsSystem(() -> lifecycle.updateStatus(state.callId, "failed", null)); }
            catch (Exception ignored) { }
        }
    }

    private static String first(String first, String second) {
        return notBlank(first) ? first : second;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static final class StreamState {
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile ScheduledFuture<?> deadlineTask;
        private volatile ScheduledFuture<?> stopTask;
        private volatile boolean started;
        private String streamSid;
        private String callSid;
        private UUID callId;
        private String providerId;
        private volatile VoiceAiSession ai;
        private volatile VoiceTransportSession transport;
    }
}
