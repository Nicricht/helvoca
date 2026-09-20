package cl.helvoca.messaging.outbound;

import cl.helvoca.messaging.meta.MetaWhatsAppAccessTokenResolver;
import cl.helvoca.messaging.meta.MetaWhatsAppCloudClient;
import cl.helvoca.messaging.meta.MetaWhatsAppProperties;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MetaWhatsAppMessagingProvider implements MessagingProvider {
    public static final String ID = "META_WHATSAPP_CLOUD";

    private final MetaWhatsAppProperties properties;
    private final PhoneNumberRepository phones;
    private final MetaWhatsAppCloudClient client;
    private final Optional<MetaWhatsAppAccessTokenResolver> accessTokens;

    public MetaWhatsAppMessagingProvider(MetaWhatsAppProperties properties,
                                         PhoneNumberRepository phones,
                                         MetaWhatsAppCloudClient client,
                                         Optional<MetaWhatsAppAccessTokenResolver> accessTokens) {
        this.properties = properties;
        this.phones = phones;
        this.client = client;
        this.accessTokens = accessTokens == null ? Optional.empty() : accessTokens;
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
        validateCommand(command);
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Meta WhatsApp integration is disabled");
        }

        List<PhoneNumber> senders = phones
                .findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(command.businessId())
                .stream()
                .filter(phone -> ID.equalsIgnoreCase(phone.getWhatsappProvider()))
                .filter(phone -> phone.getWhatsappExternalId() != null && !phone.getWhatsappExternalId().isBlank())
                .toList();

        if (senders.size() != 1) {
            throw new IllegalStateException(senders.isEmpty()
                    ? "Tenant has no Meta WhatsApp sender"
                    : "Tenant has multiple Meta WhatsApp senders; configuration is ambiguous");
        }

        String accessToken = accessTokens
                .flatMap(resolver -> resolver.resolve(command.businessId()))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Meta WhatsApp access token is not configured for tenant"));

        PhoneNumber sender = senders.getFirst();
        String providerMessageId = client.sendText(
                sender.getWhatsappExternalId(),
                accessToken,
                command.recipient(),
                command.content());

        return new SendResult(providerMessageId);
    }

    private void validateCommand(SendCommand command) {
        if (command == null || command.businessId() == null || command.messageId() == null) {
            throw new IllegalArgumentException("Invalid outbound command");
        }
        if (!supports(command.channel())) {
            throw new IllegalArgumentException("Unsupported outbound channel");
        }
        if (command.recipient() == null || command.recipient().isBlank()) {
            throw new IllegalArgumentException("WhatsApp recipient is required");
        }
        if (command.content() == null || command.content().isBlank()) {
            throw new IllegalArgumentException("Outbound content is required");
        }
    }
}
