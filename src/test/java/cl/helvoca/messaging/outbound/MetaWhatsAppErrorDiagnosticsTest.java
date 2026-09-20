package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import cl.helvoca.messaging.meta.MetaWhatsAppApiException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppErrorDiagnosticsTest {

    @Test
    void nonTransientMetaApiErrorBecomesPermanentJobWithSafeDiagnostics() {
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(true);
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);
        MessagingProvider provider = mock(MessagingProvider.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);

        UUID businessId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        MessagingMessage inbound = inbound(conversationId);
        MessagingConversation conversation = conversation(businessId);

        when(messages.findById(messageId)).thenReturn(Optional.of(inbound));
        when(conversations.findByIdAndBusinessId(conversationId, businessId))
                .thenReturn(Optional.of(conversation));
        when(providers.require(MetaWhatsAppMessagingProvider.ID, OutboundMessage.Channel.WHATSAPP))
                .thenReturn(provider);
        when(provider.send(any())).thenThrow(new MetaWhatsAppApiException(
                400, "131047", "2494010", false, "OAuthException", "TRACE_SAFE"));

        var handler = new MetaWhatsAppAssistantReplyJobHandler(
                properties, providers, messages, conversations);

        var error = assertThrows(
                cl.helvoca.jobs.PersistentJobHandler.PermanentJobException.class,
                () -> handler.handle(job(businessId, messageId)));

        assertTrue(error.getMessage().contains("code=131047"));
        assertTrue(error.getMessage().contains("subcode=2494010"));
        assertFalse(error.getMessage().contains("+56911111111"));
    }

    @Test
    void transientMetaApiErrorRemainsRetryable() {
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(true);
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);
        MessagingProvider provider = mock(MessagingProvider.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);

        UUID businessId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(messages.findById(messageId)).thenReturn(Optional.of(inbound(conversationId)));
        when(conversations.findByIdAndBusinessId(conversationId, businessId))
                .thenReturn(Optional.of(conversation(businessId)));
        when(providers.require(MetaWhatsAppMessagingProvider.ID, OutboundMessage.Channel.WHATSAPP))
                .thenReturn(provider);
        when(provider.send(any())).thenThrow(new MetaWhatsAppApiException(
                503, "2", null, true, "OAuthException", "TRACE_RETRY"));

        var handler = new MetaWhatsAppAssistantReplyJobHandler(
                properties, providers, messages, conversations);

        assertThrows(
                cl.helvoca.jobs.PersistentJobHandler.RetryableJobException.class,
                () -> handler.handle(job(businessId, messageId)));
    }

    private static MessagingMessage inbound(UUID conversationId) {
        MessagingMessage inbound = new MessagingMessage();
        inbound.setConversationId(conversationId);
        inbound.setExternalMessageId("wamid.inbound-diagnostic");
        inbound.setDirection("INBOUND");
        inbound.setRole("USER");
        inbound.setContent("Hola");
        inbound.setReplyText("Respuesta IA");
        return inbound;
    }

    private static MessagingConversation conversation(UUID businessId) {
        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(businessId);
        conversation.setChannel("whatsapp");
        conversation.setSender("+56911111111");
        conversation.setRecipient("+56922222222");
        return conversation;
    }

    private static PersistentJob job(UUID businessId, UUID messageId) {
        Instant now = Instant.now();
        return new PersistentJob(
                UUID.randomUUID(),
                businessId,
                null,
                PersistentJob.Type.META_WHATSAPP_AI_REPLY,
                PersistentJob.Status.RUNNING,
                "meta-ai-reply:wamid.inbound-diagnostic",
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
