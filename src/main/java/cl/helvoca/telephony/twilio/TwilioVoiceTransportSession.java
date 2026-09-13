package cl.helvoca.telephony.twilio;

import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

final class TwilioVoiceTransportSession implements VoiceTransportSession {
    private static final Logger log = LoggerFactory.getLogger(TwilioVoiceTransportSession.class);

    private final WebSocketSession socket;
    private final String streamSid;
    private final String accountSid;
    private final String callSid;
    private final TwilioCallControl control;
    private final Object sendLock = new Object();

    TwilioVoiceTransportSession(WebSocketSession socket,
                                String streamSid,
                                String accountSid,
                                String callSid,
                                TwilioCallControl control) {
        this.socket = socket;
        this.streamSid = streamSid;
        this.accountSid = accountSid;
        this.callSid = callSid;
        this.control = control;
    }

    @Override
    public String id() {
        return streamSid;
    }

    @Override
    public boolean isOpen() {
        return socket.isOpen();
    }

    @Override
    public void sendAudio(String streamId, String base64Audio) {
        if (!matches(streamId) || base64Audio == null || base64Audio.isBlank()) return;
        send(new JSONObject()
                .put("event", "media")
                .put("streamSid", streamSid)
                .put("media", new JSONObject().put("payload", base64Audio)));
    }

    @Override
    public void clearPlayback(String streamId) {
        if (!matches(streamId)) return;
        send(new JSONObject().put("event", "clear").put("streamSid", streamSid));
    }

    @Override
    public boolean transferToHuman(String targetPhone) {
        return control.transferToHuman(accountSid, callSid, targetPhone);
    }

    @Override
    public void closeOnUpstreamFailure() {
        control.hangup(accountSid, callSid);
        try {
            if (socket.isOpen()) socket.close(CloseStatus.SERVER_ERROR);
        } catch (IOException e) {
            log.debug("Could not close failed Twilio media socket call={}: {}", callSid, e.getMessage());
        }
    }

    private void send(JSONObject payload) {
        synchronized (sendLock) {
            if (!socket.isOpen()) return;
            try {
                socket.sendMessage(new TextMessage(payload.toString()));
            } catch (IOException e) {
                log.warn("Could not send Twilio media message call={} stream={}: {}",
                        callSid, streamSid, e.getMessage());
            }
        }
    }

    private boolean matches(String streamId) {
        return streamId != null && streamId.equals(streamSid);
    }
}
