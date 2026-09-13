package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiProviderRegistry;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import org.json.JSONObject;
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

@Component
public class TwilioMediaStreamHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(TwilioMediaStreamHandler.class);

    private final TwilioMediaRouteSigner routeSigner;
    private final VoiceAiProviderRegistry providers;
    private final VoiceProviderHealthRegistry health;
    private final CallLifecycleService lifecycle;
    private final TwilioCallControl callControl;
    private final ConcurrentMap<String, StreamState> states = new ConcurrentHashMap<>();

    public TwilioMediaStreamHandler(TwilioMediaRouteSigner routeSigner,
                                    VoiceAiProviderRegistry providers,
                                    VoiceProviderHealthRegistry health,
                                    CallLifecycleService lifecycle,
                                    TwilioCallControl callControl) {
        this.routeSigner = routeSigner;
        this.providers = providers;
        this.health = health;
        this.lifecycle = lifecycle;
        this.callControl = callControl;
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
            case "media" -> handleMedia(state, event);
            case "stop" -> closeState(state, false);
            case "mark", "dtmf" -> { }
            default -> log.debug("Ignoring Twilio media event={} socket={}",
                    event.optString("event", ""), session.getId());
        }
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
        String issuedAt = parameters.optString("issuedAt", null);
        String route = parameters.optString("route", null);

        boolean valid = notBlank(streamSid)
                && notBlank(callSid)
                && callSid.equals(routedCallSid)
                && routeSigner.verify(businessPhone, callerPhone, callSid, providerId, issuedAt, route);
        if (!valid) {
            log.warn("Rejected invalid Twilio media route socket={} call={}", socket.getId(), callSid);
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

        UUID callId = lifecycle.startInboundCall("twilio", callSid, callerPhone, businessPhone);
        RealtimeCallContext context = lifecycle.markStreamStarted(callId, callSid, streamSid, provider.id());
        if (provider.certificationSession(context)) {
            lifecycle.markCertification(callId);
            log.info("RECEPVOZ_CALL_CERTIFICATION armed call={} provider={}", callId, provider.id());
        }
        TwilioVoiceTransportSession transport = new TwilioVoiceTransportSession(
                socket, streamSid, accountSid, callSid, callControl);
        VoiceAiSession ai = provider.createSession(context, transport);

        state.started = true;
        state.streamSid = streamSid;
        state.callSid = callSid;
        state.callId = callId;
        state.providerId = provider.id();
        state.ai = ai;
        try {
            ai.start();
        } catch (RuntimeException e) {
            lifecycle.updateStatus(callId, "failed", null);
            health.failure(provider.id(), VoiceProviderHealthRegistry.FailureKind.UPSTREAM, e.getMessage());
            transport.closeOnUpstreamFailure();
            throw e;
        }
        log.info("Twilio media stream started call={} stream={} provider={}", callSid, streamSid, provider.id());
    }

    private void handleMedia(StreamState state, JSONObject event) {
        if (!state.started || state.ai == null) return;
        JSONObject media = event.optJSONObject("media");
        if (media == null) return;
        String track = media.optString("track", "inbound");
        if (!"inbound".equalsIgnoreCase(track) && !"inbound_track".equalsIgnoreCase(track)) return;
        String payload = media.optString("payload", null);
        if (payload != null && !payload.isBlank()) state.ai.acceptInboundAudio(payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        StreamState state = states.get(session.getId());
        if (state != null && state.callId != null) {
            try { lifecycle.updateStatus(state.callId, "failed", null); }
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

    private void closeState(StreamState state, boolean failed) {
        VoiceAiSession ai = state.ai;
        state.ai = null;
        if (state.streamSid != null) {
            try { lifecycle.markStreamStopped(state.streamSid); }
            catch (Exception ignored) { }
        }
        if (ai != null) {
            try { ai.close(); }
            catch (Exception ignored) { }
        }
        if (failed && state.callId != null) {
            try { lifecycle.updateStatus(state.callId, "failed", null); }
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
        private boolean started;
        private String streamSid;
        private String callSid;
        private UUID callId;
        private String providerId;
        private VoiceAiSession ai;
    }
}
