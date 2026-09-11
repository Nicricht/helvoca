package cl.helvoca.telephony.twilio;

import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Dedicated handshake guard for the Twilio Console outbound demo stream.
 *
 * <p>The normal production Media Stream remains protected by
 * {@link TwilioMediaStreamHandshakeInterceptor}. Twilio Console "Try out Voice"
 * has different signature behavior, so this separate route is enabled only
 * while trial mode is explicitly on. The stream itself still has to present a
 * valid callId/CallSid pair created by the outbound-test webhook before the
 * realtime handler will attach it to a call.</p>
 */
@Component
public class TwilioTrialMediaStreamHandshakeInterceptor implements HandshakeInterceptor {
    private final TrialVoiceProperties trial;

    public TwilioTrialMediaStreamHandshakeInterceptor(TrialVoiceProperties trial) {
        this.trial = trial;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!trial.isEnabled()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
