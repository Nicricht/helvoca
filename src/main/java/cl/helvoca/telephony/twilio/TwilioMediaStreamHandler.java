package cl.helvoca.telephony.twilio;

import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

@Component
public class TwilioMediaStreamHandler extends TextWebSocketHandler {
    private final TwilioCallService calls;

    public TwilioMediaStreamHandler(TwilioCallService calls) { this.calls = calls; }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JSONObject json = new JSONObject(message.getPayload());
        String event = json.optString("event");
        switch (event) {
            case "start" -> handleStart(session, json);
            case "media" -> {
                // Sprint 3 terminates the Twilio transport here. Sprint 4 will bridge media.payload to OpenAI Realtime.
            }
            case "stop" -> calls.markStreamStopped(json.optString("streamSid"));
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
        try {
            calls.markStreamStarted(UUID.fromString(callIdValue), callSid, streamSid);
        } catch (RuntimeException e) {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }
}
