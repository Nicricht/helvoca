package cl.helvoca.messaging.outbound;

import cl.helvoca.messaging.meta.MetaWhatsAppProperties;
import org.springframework.stereotype.Component;

/**
 * Meta WhatsApp Cloud provider boundary.
 *
 * Transport is intentionally fail-closed until the next delivery block wires
 * tenant credentials and the Graph API request. Registering this provider now
 * lets the existing outbound pipeline reference a stable provider id without
 * risking a real send.
 */
@Component
public class MetaWhatsAppMessagingProvider implements MessagingProvider {
    public static final String ID = "META_WHATSAPP_CLOUD";

    private final MetaWhatsAppProperties properties;

    public MetaWhatsAppMessagingProvider(MetaWhatsAppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(OutboundMessage.Channel channel) {
        return channel == OutboundMessage.Channel.WHATSAPP;
    }

    @Override
    public SendResult send(SendCommand command) {
        if (command == null || command.businessId() == null || command.messageId() == null) {
            throw new IllegalArgumentException("Invalid outbound command");
        }
        if (!supports(command.channel())) {
            throw new IllegalArgumentException("Unsupported outbound channel");
        }
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Meta WhatsApp integration is disabled");
        }
        if (command.recipient() == null || command.recipient().isBlank()) {
            throw new IllegalArgumentException("WhatsApp recipient is required");
        }
        if (command.content() == null || command.content().isBlank()) {
            throw new IllegalArgumentException("Outbound content is required");
        }

        throw new IllegalStateException("Meta WhatsApp outbound transport is not implemented yet");
    }
}
