package cl.helvoca.telephony.twilio;

import cl.helvoca.call.CallTraceService;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

/**
 * Twilio-specific implementation of the provider-neutral voice transport port.
 */
public final class TwilioVoiceTransportSession implements VoiceTransportSession {
    private static final Logger log = LoggerFactory.getLogger(TwilioVoiceTransportSession.class);

    private final WebSocketSession session;
    private final String accountSid;
    private final String callSid;
    private final TwilioCallControl callControl;
    private final CallTraceService trace;
    private final UUID businessId;
    private final UUID callId;

    public TwilioVoiceTransportSession(WebSocketSession session,
                                       String accountSid,
                                       String callSid,
                                       TwilioCallControl callControl) {
        this(session, accountSid, callSid, callControl, null, null, null);
    }

    public TwilioVoiceTransportSession(WebSocketSession session,
                                       String accountSid,
                                       String callSid,
                                       TwilioCallControl callControl,
                                       CallTraceService trace,
                                       UUID businessId,
                                       UUID callId) {
        this.session = session;
        this.accountSid = accountSid;
        this.callSid = callSid;
        this.callControl = callControl;
        this.trace = trace;
        this.businessId = businessId;
        this.callId = callId;
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
    public boolean transferToHuman(String targetPhone) {
        if (!session.isOpen()) return false;
        boolean accepted = callControl.transferToHuman(accountSid, callSid, targetPhone);
        if (trace != null && businessId != null && callId != null) {
            try {
                trace.recordHumanTransfer(businessId, callId, accepted, targetPhone);
            } catch (Exception e) {
                log.warn("Could not persist human transfer trace call={}: {}", callId, e.getMessage());
            }
        }
        return accepted;
    }

    @Override
    public void closeOnUpstreamFailure() {
        // Prefer changing the live call to a short spoken fallback. Twilio will
        // then close the Media Stream as it begins executing the replacement
        // TwiML. If the REST update itself fails, close the WebSocket so the
        // caller is never left attached to a dead AI stream indefinitely.
        if (callControl.failGracefully(accountSid, callSid)) return;
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
