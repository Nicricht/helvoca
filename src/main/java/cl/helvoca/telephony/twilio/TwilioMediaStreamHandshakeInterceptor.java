package cl.helvoca.telephony.twilio;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class TwilioMediaStreamHandshakeInterceptor implements HandshakeInterceptor {
    private final TwilioSignatureValidator validator;

    public TwilioMediaStreamHandshakeInterceptor(TwilioSignatureValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String signature = request.getHeaders().getFirst("X-Twilio-Signature");
        boolean valid = validator.validateWebSocket(request.getURI().toString(), signature);
        if (!valid) response.setStatusCode(HttpStatus.FORBIDDEN);
        return valid;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
