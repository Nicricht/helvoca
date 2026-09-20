package cl.helvoca.messaging.meta;

import org.springframework.stereotype.Service;

@Service
public class MetaWhatsAppEmbeddedSignupBootstrapService {
    private final MetaWhatsAppProperties metaProperties;

    public MetaWhatsAppEmbeddedSignupBootstrapService(MetaWhatsAppProperties metaProperties) {
        this.metaProperties = metaProperties;
    }

    public MetaWhatsAppEmbeddedSignupBootstrapResponse bootstrap() {
        boolean enabled = metaProperties.isEmbeddedSignupEnabled();
        boolean available = enabled
                && metaProperties.hasEmbeddedSignupAppId()
                && metaProperties.hasEmbeddedSignupConfigId();

        return new MetaWhatsAppEmbeddedSignupBootstrapResponse(
                enabled,
                available,
                available ? metaProperties.getEmbeddedSignupAppId() : null,
                available ? metaProperties.getEmbeddedSignupConfigId() : null,
                metaProperties.getGraphApiVersion());
    }
}
