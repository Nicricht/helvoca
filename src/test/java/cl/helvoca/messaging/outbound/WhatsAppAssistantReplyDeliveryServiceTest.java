package cl.helvoca.messaging.outbound;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class WhatsAppAssistantReplyDeliveryServiceTest {

    @Test
    void deliveryDisabledDoesNotResolveOrCallProvider() {
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(false);
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);

        var service = new WhatsAppAssistantReplyDeliveryService(properties, providers);
        service.scheduleMetaReply(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "wamid.test",
                MetaWhatsAppMessagingProvider.ID,
                "+56911111111",
                "Respuesta IA");

        verifyNoInteractions(providers);
    }

    @Test
    void nonMetaTenantDoesNotUseMetaProvider() {
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(true);
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);

        var service = new WhatsAppAssistantReplyDeliveryService(properties, providers);
        service.scheduleMetaReply(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SM-test",
                "TWILIO_WHATSAPP",
                "+56911111111",
                "Respuesta IA");

        verifyNoInteractions(providers);
    }

    @Test
    void enabledMetaReplyUsesMetaProviderPipeline() {
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(true);
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);
        MessagingProvider provider = mock(MessagingProvider.class);

        when(providers.require(
                MetaWhatsAppMessagingProvider.ID,
                OutboundMessage.Channel.WHATSAPP))
                .thenReturn(provider);
        when(provider.send(any(MessagingProvider.SendCommand.class)))
                .thenReturn(new MessagingProvider.SendResult("wamid.provider-1"));

        UUID businessId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        var service = new WhatsAppAssistantReplyDeliveryService(properties, providers);

        service.scheduleMetaReply(
                businessId,
                messageId,
                "wamid.inbound-1",
                MetaWhatsAppMessagingProvider.ID,
                "+56911111111",
                "Respuesta IA");

        verify(provider).send(argThat(command ->
                businessId.equals(command.businessId())
                        && messageId.equals(command.messageId())
                        && command.channel() == OutboundMessage.Channel.WHATSAPP
                        && "+56911111111".equals(command.recipient())
                        && "Respuesta IA".equals(command.content())
                        && "META_AI_REPLY:wamid.inbound-1".equals(command.idempotencyKey())));
    }
}
