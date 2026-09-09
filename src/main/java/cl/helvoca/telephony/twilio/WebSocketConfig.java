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

    public WebSocketConfig(TwilioMediaStreamHandler handler,
                           TwilioMediaStreamHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/twilio")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*");
    }
}
