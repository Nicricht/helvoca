package cl.helvoca.telephony.twilio;

import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Twilio-specific implementation of the provider-neutral voice transport port.
 */
public final class TwilioVoiceTransportSession implements VoiceTransportSession {
    private static final Logger log = LoggerFactory.getLogger(TwilioVoiceTransportSession.class);

    private final WebSocketSession session;

    public TwilioVoiceTransportSession(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public String id() {
        return session.getId();
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void sendAudio(String streamId, String base64Audio) {
        if (base64Audio == null || base64Audio.isBlank()) return;
        send(new JSONObject()
                .put("event", "media")
                .put("streamSid", streamId)
                .put("media", new JSONObject().put("payload", base64Audio)));
    }

    @Override
    public void clearPlayback(String streamId) {
        send(new JSONObject().put("event", "clear").put("streamSid", streamId));
    }

    @Override
    public void closeOnUpstreamFailure() {
        try {
            if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception e) {
            log.warn("Could not close Twilio transport session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void send(JSONObject payload) {
        try {
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(payload.toString()));
            }
        } catch (Exception e) {
            log.warn("Could not send Twilio media event on session {}: {}", session.getId(), e.getMessage());
        }
    }
}
