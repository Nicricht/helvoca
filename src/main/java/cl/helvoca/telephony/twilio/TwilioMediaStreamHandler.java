package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiProviderRegistry;
import cl.helvoca.voice.VoiceAiSession;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TwilioMediaStreamHandler extends TextWebSocketHandler {
    private final TwilioCallService calls;
    private final VoiceAiProviderRegistry aiProviders;
    private final Map<String, VoiceAiSession> sessions = new ConcurrentHashMap<>();

    public TwilioMediaStreamHandler(TwilioCallService calls,
                                    VoiceAiProviderRegistry aiProviders) {
        this.calls = calls;
        this.aiProviders = aiProviders;
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
                // connected, mark and provider extension events are safe to ignore at this layer.
            }
        }
    }

    private void handleStart(WebSocketSession session, JSONObject json) throws Exception {
        JSONObject start = json.optJSONObject("start");
        if (start == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        JSONObject custom = start.optJSONObject("customParameters");
        String callIdValue = custom == null ? null : custom.optString("callId", null);
        String callSid = start.optString("callSid", null);
        String streamSid = json.optString("streamSid", null);
        if (callIdValue == null || callSid == null || streamSid == null) {
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
            RealtimeCallContext context = calls.markStreamStarted(UUID.fromString(callIdValue), callSid, streamSid);
            VoiceAiSession aiSession = aiProvider.createSession(context, new TwilioVoiceTransportSession(session));
            sessions.put(session.getId(), aiSession);
            aiSession.start();
        } catch (RuntimeException e) {
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
        calls.markStreamStopped(json.optString("streamSid"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        VoiceAiSession aiSession = sessions.remove(session.getId());
        if (aiSession != null) aiSession.close();
    }
}
