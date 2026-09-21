package cl.helvoca.messaging.outbound;

import cl.helvoca.audit.AuditService;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MetaWhatsAppDeliveryStatusServiceTest {

    @Test
    void deliveredUpdatesPersistedAiReplyTracking() {
        OutboundMessageRepository outbound = mock(OutboundMessageRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Instant occurredAt = Instant.ofEpochSecond(1720000000L);

        MessagingMessage message = new MessagingMessage();
        message.setConversationId(conversationId);
        message.setProvider(MetaWhatsAppMessagingProvider.ID);
        message.setProviderMessageId("wamid.REPLY-1");
        message.setProviderDeliveryStatus("SENT");

        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(businessId);

        when(outbound.findTopByProviderAndProviderMessageIdOrderByUpdatedAtDesc(
                MetaWhatsAppMessagingProvider.ID, "wamid.REPLY-1"))
                .thenReturn(Optional.empty());
        when(messages.findByProviderAndProviderMessageId(
                MetaWhatsAppMessagingProvider.ID, "wamid.REPLY-1"))
                .thenReturn(Optional.of(message));
        when(conversations.findByIdAndBusinessId(conversationId, businessId))
                .thenReturn(Optional.of(conversation));

        var result = new MetaWhatsAppDeliveryStatusService(outbound, messages, conversations)
                .apply(businessId, "wamid.REPLY-1", "delivered", occurredAt, null);

        assertEquals(MetaWhatsAppDeliveryStatusService.Result.UPDATED, result);
        assertEquals("DELIVERED", message.getProviderDeliveryStatus());
        assertEquals(occurredAt, message.getDeliveredAt());
        assertEquals(occurredAt, message.getDeliveryUpdatedAt());
        verify(messages).saveAndFlush(message);
    }

    @Test
    void firstDeliveredCallbackCertifiesMetaSenderAndAuditsSuccess() {
        OutboundMessageRepository outbound = mock(OutboundMessageRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        Instant occurredAt = Instant.ofEpochSecond(1720000000L);

        OutboundMessage message = mock(OutboundMessage.class);
        when(message.getBusinessId()).thenReturn(businessId);
        when(message.getProviderDeliveryStatus()).thenReturn("SENT");
        when(outbound.findTopByProviderAndProviderMessageIdOrderByUpdatedAtDesc(
                MetaWhatsAppMessagingProvider.ID, "wamid.CERT-1"))
                .thenReturn(Optional.of(message));

        PhoneNumber sender = mock(PhoneNumber.class);
        when(sender.getId()).thenReturn(phoneId);
        when(sender.getWhatsappProvider()).thenReturn(MetaWhatsAppMessagingProvider.ID);
        when(sender.getWhatsappCertifiedAt()).thenReturn(null);
        when(phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(sender));

        var result = new MetaWhatsAppDeliveryStatusService(
                outbound, messages, conversations, phones, audit)
                .apply(businessId, "wamid.CERT-1", "delivered", occurredAt, null);

        assertEquals(MetaWhatsAppDeliveryStatusService.Result.UPDATED, result);
        verify(sender).setWhatsappCertifiedAt(occurredAt);
        verify(phones).saveAndFlush(sender);
        verify(audit).success(
                businessId,
                "META_WHATSAPP_CERTIFICATION_COMPLETED",
                "WHATSAPP_SENDER",
                phoneId);
    }

    @Test
    void readDoesNotRegressWhenLateSentCallbackArrives() {
        OutboundMessageRepository outbound = mock(OutboundMessageRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        MessagingMessage message = new MessagingMessage();
        message.setConversationId(conversationId);
        message.setProvider(MetaWhatsAppMessagingProvider.ID);
        message.setProviderMessageId("wamid.REPLY-2");
        message.setProviderDeliveryStatus("READ");

        when(outbound.findTopByProviderAndProviderMessageIdOrderByUpdatedAtDesc(
                MetaWhatsAppMessagingProvider.ID, "wamid.REPLY-2"))
                .thenReturn(Optional.empty());
        when(messages.findByProviderAndProviderMessageId(
                MetaWhatsAppMessagingProvider.ID, "wamid.REPLY-2"))
                .thenReturn(Optional.of(message));
        when(conversations.findByIdAndBusinessId(conversationId, businessId))
                .thenReturn(Optional.of(new MessagingConversation()));

        var result = new MetaWhatsAppDeliveryStatusService(outbound, messages, conversations)
                .apply(businessId, "wamid.REPLY-2", "sent", Instant.now(), null);

        assertEquals(MetaWhatsAppDeliveryStatusService.Result.IGNORED, result);
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void failedUpdatesExistingOutboundMessageWithSafeMetaCode() {
        OutboundMessageRepository outbound = mock(OutboundMessageRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        UUID businessId = UUID.randomUUID();

        OutboundMessage message = mock(OutboundMessage.class);
        when(message.getBusinessId()).thenReturn(businessId);
        when(message.getProviderDeliveryStatus()).thenReturn("SENT");
        when(outbound.findTopByProviderAndProviderMessageIdOrderByUpdatedAtDesc(
                MetaWhatsAppMessagingProvider.ID, "wamid.OUT-1"))
                .thenReturn(Optional.of(message));

        var result = new MetaWhatsAppDeliveryStatusService(outbound, messages, conversations)
                .apply(businessId, "wamid.OUT-1", "failed", Instant.now(), "131047");

        assertEquals(MetaWhatsAppDeliveryStatusService.Result.UPDATED, result);
        verify(message).setProviderDeliveryStatus("FAILED");
        verify(message).setFailureCode("META_131047");
        verify(outbound).saveAndFlush(message);
        verifyNoInteractions(messages, conversations);
    }
}
