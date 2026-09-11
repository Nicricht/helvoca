package cl.helvoca.telephony.twilio;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final TwilioMediaStreamHandler handler;
    private final TwilioMediaStreamHandshakeInterceptor interceptor;
    private final TwilioTrialMediaStreamHandshakeInterceptor trialInterceptor;

    public WebSocketConfig(TwilioMediaStreamHandler handler,
                           TwilioMediaStreamHandshakeInterceptor interceptor,
                           TwilioTrialMediaStreamHandshakeInterceptor trialInterceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.trialInterceptor = trialInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/twilio")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*");

        registry.addHandler(handler, "/ws/twilio-trial")
                .addInterceptors(trialInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
