package cl.helvoca.messaging.outbound;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.outbound")
public class OutboundMessagingProperties {
    private boolean deliveryEnabled = false;
    private String provider = "NONE";

    public boolean isDeliveryEnabled() { return deliveryEnabled; }
    public void setDeliveryEnabled(boolean deliveryEnabled) { this.deliveryEnabled = deliveryEnabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}
