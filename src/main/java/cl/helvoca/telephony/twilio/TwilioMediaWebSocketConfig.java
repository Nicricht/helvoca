package cl.helvoca.telephony.twilio;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class TwilioMediaWebSocketConfig implements WebSocketConfigurer {
    private final TwilioMediaStreamHandler handler;
    private final TwilioProperties properties;

    public TwilioMediaWebSocketConfig(TwilioMediaStreamHandler handler,
                                      TwilioProperties properties) {
        this.handler = handler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String path = properties.getMediaStreamPath();
        if (path == null || path.isBlank()) path = "/ws/v1/twilio/media";
        if (!path.startsWith("/")) path = "/" + path;
        registry.addHandler(handler, path).setAllowedOrigins("*");
    }
}
