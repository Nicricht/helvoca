package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class MetaWhatsAppAssistantReplyJobHandlerTest {

    @Test
    void durableJobLoadsPersistedReplyAndUsesMetaProvider() {
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(true);
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);
        MessagingProvider provider = mock(MessagingProvider.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);

        UUID businessId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        MessagingMessage inbound = new MessagingMessage();
        inbound.setConversationId(conversationId);
        inbound.setExternalMessageId("wamid.inbound-1");
        inbound.setDirection("INBOUND");
        inbound.setRole("USER");
        inbound.setContent("Hola");
        inbound.setReplyText("Respuesta IA");

        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(businessId);
        conversation.setChannel("whatsapp");
        conversation.setSender("+56911111111");
        conversation.setRecipient("+56922222222");

        when(messages.findById(messageId)).thenReturn(Optional.of(inbound));
        when(conversations.findByIdAndBusinessId(conversationId, businessId))
                .thenReturn(Optional.of(conversation));
        when(providers.require(
                MetaWhatsAppMessagingProvider.ID,
                OutboundMessage.Channel.WHATSAPP))
                .thenReturn(provider);
        when(provider.send(any(MessagingProvider.SendCommand.class)))
                .thenReturn(new MessagingProvider.SendResult("wamid.provider-1"));

        var handler = new MetaWhatsAppAssistantReplyJobHandler(
                properties, providers, messages, conversations);

        handler.handle(job(businessId, messageId));

        verify(provider).send(argThat(command ->
                businessId.equals(command.businessId())
                        && messageId.equals(command.messageId())
                        && command.channel() == OutboundMessage.Channel.WHATSAPP
                        && "+56911111111".equals(command.recipient())
                        && "Respuesta IA".equals(command.content())
                        && "meta-ai-reply:wamid.inbound-1".equals(command.idempotencyKey())));
    }

    @Test
    void deliveryDisabledFailsClosedBeforeResolvingProvider() {
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(false);
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);

        var handler = new MetaWhatsAppAssistantReplyJobHandler(
                properties,
                providers,
                mock(MessagingMessageRepository.class),
                mock(MessagingConversationRepository.class));

        try {
            handler.handle(job(UUID.randomUUID(), UUID.randomUUID()));
        } catch (RuntimeException ignored) {
        }

        verifyNoInteractions(providers);
    }

    private static PersistentJob job(UUID businessId, UUID messageId) {
        Instant now = Instant.now();
        return new PersistentJob(
                UUID.randomUUID(),
                businessId,
                null,
                PersistentJob.Type.META_WHATSAPP_AI_REPLY,
                PersistentJob.Status.RUNNING,
                "meta-ai-reply:wamid.inbound-1",
                new JSONObject().put("messageId", messageId.toString()).toString(),
                1,
                5,
                now,
                "worker",
                now.plusSeconds(120),
                null,
                null,
                null,
                now,
                now);
    }
}
