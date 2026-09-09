package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.OpenAiRealtimeBridge;
import cl.helvoca.ai.realtime.OpenAiRealtimeBridgeFactory;
import cl.helvoca.ai.realtime.RealtimeCallContext;
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
    private final OpenAiRealtimeBridgeFactory realtime;
    private final Map<String, OpenAiRealtimeBridge> bridges = new ConcurrentHashMap<>();

    public TwilioMediaStreamHandler(TwilioCallService calls, OpenAiRealtimeBridgeFactory realtime) {
        this.calls = calls;
        this.realtime = realtime;
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
        if (!realtime.configured()) {
            session.close(CloseStatus.SERVER_ERROR.withReason("Realtime AI is not configured"));
            return;
        }
        try {
            RealtimeCallContext context = calls.markStreamStarted(UUID.fromString(callIdValue), callSid, streamSid);
            OpenAiRealtimeBridge bridge = realtime.create(context, session);
            bridges.put(session.getId(), bridge);
            bridge.start();
        } catch (RuntimeException e) {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    private void handleMedia(WebSocketSession session, JSONObject json) {
        OpenAiRealtimeBridge bridge = bridges.get(session.getId());
        if (bridge == null) return;
        JSONObject media = json.optJSONObject("media");
        if (media == null) return;
        bridge.acceptTwilioAudio(media.optString("payload", null));
    }

    private void handleStop(WebSocketSession session, JSONObject json) {
        OpenAiRealtimeBridge bridge = bridges.remove(session.getId());
        if (bridge != null) bridge.close();
        calls.markStreamStopped(json.optString("streamSid"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        OpenAiRealtimeBridge bridge = bridges.remove(session.getId());
        if (bridge != null) bridge.close();
    }
}
