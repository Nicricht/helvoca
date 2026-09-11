package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiProviderRegistry;
import cl.helvoca.voice.VoiceAiSession;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TwilioMediaStreamHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(TwilioMediaStreamHandler.class);

    private final TwilioCallService calls;
    private final VoiceAiProviderRegistry aiProviders;
    private final TwilioCallControl callControl;
    private final Map<String, VoiceAiSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> streamIds = new ConcurrentHashMap<>();

    public TwilioMediaStreamHandler(TwilioCallService calls,
                                    VoiceAiProviderRegistry aiProviders,
                                    TwilioCallControl callControl) {
        this.calls = calls;
        this.aiProviders = aiProviders;
        this.callControl = callControl;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JSONObject json = new JSONObject(message.getPayload());
        String event = json.optString("event");
        switch (event) {
            case "start" -> handleStart(session, json);
            case "media" -> handleMedia(session, json);
            case "stop" -> handleStop(session, json);
            default -> {
                // connected, mark, DTMF and provider extension events are safe to
                // ignore at this layer for the current production voice contract.
            }
        }
    }

    private void handleStart(WebSocketSession session, JSONObject json) throws Exception {
        if (sessions.containsKey(session.getId())) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Duplicate stream start"));
            return;
        }

        JSONObject start = json.optJSONObject("start");
        if (start == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        JSONObject custom = start.optJSONObject("customParameters");
        JSONObject mediaFormat = start.optJSONObject("mediaFormat");
        String callIdValue = custom == null ? null : custom.optString("callId", null);
        String accountSid = start.optString("accountSid", null);
        String callSid = start.optString("callSid", null);
        String nestedStreamSid = start.optString("streamSid", null);
        String streamSid = json.optString("streamSid", nestedStreamSid);

        if (callIdValue == null || accountSid == null || callSid == null || streamSid == null
                || (nestedStreamSid != null && !Objects.equals(nestedStreamSid, streamSid))
                || !supportedMediaFormat(mediaFormat)) {
            log.warn("Rejected malformed Twilio Media Stream start session={}", session.getId());
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        VoiceAiProvider aiProvider;
        try {
            aiProvider = aiProviders.active();
        } catch (IllegalStateException e) {
            session.close(CloseStatus.SERVER_ERROR.withReason("Voice AI provider is invalid"));
            return;
        }
        if (!aiProvider.configured()) {
            session.close(CloseStatus.SERVER_ERROR.withReason("Voice AI provider is not configured"));
            return;
        }

        try {
            RealtimeCallContext context = calls.markStreamStarted(
                    UUID.fromString(callIdValue), callSid, streamSid, aiProvider.id());
            VoiceAiSession aiSession = aiProvider.createSession(
                    context,
                    new TwilioVoiceTransportSession(session, accountSid, callSid, callControl));
            streamIds.put(session.getId(), streamSid);
            sessions.put(session.getId(), aiSession);
            aiSession.start();
            log.info("Twilio Media Stream started call={} stream={} ai={}", callSid, streamSid, aiProvider.id());
        } catch (RuntimeException e) {
            streamIds.remove(session.getId());
            sessions.remove(session.getId());
            log.warn("Could not start Twilio Media Stream session {}: {}", session.getId(), e.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    private void handleMedia(WebSocketSession session, JSONObject json) {
        VoiceAiSession aiSession = sessions.get(session.getId());
        if (aiSession == null) return;
        JSONObject media = json.optJSONObject("media");
        if (media == null) return;
        aiSession.acceptInboundAudio(media.optString("payload", null));
    }

    private void handleStop(WebSocketSession session, JSONObject json) {
        VoiceAiSession aiSession = sessions.remove(session.getId());
        if (aiSession != null) aiSession.close();
        String tracked = streamIds.remove(session.getId());
        String streamSid = json.optString("streamSid", tracked);
        calls.markStreamStopped(streamSid);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        VoiceAiSession aiSession = sessions.remove(session.getId());
        if (aiSession != null) aiSession.close();
        String streamSid = streamIds.remove(session.getId());
        if (streamSid != null) calls.markStreamStopped(streamSid);
        log.info("Twilio Media Stream closed session={} status={} stream={}", session.getId(), status.getCode(), streamSid);
    }

    private static boolean supportedMediaFormat(JSONObject format) {
        return format != null
                && "audio/x-mulaw".equalsIgnoreCase(format.optString("encoding", ""))
                && format.optInt("sampleRate", -1) == 8000
                && format.optInt("channels", -1) == 1;
    }
}
