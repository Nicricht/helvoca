package cl.helvoca.messaging;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MessagingConversationQueryServiceTest {

    @Test
    void listUsesAuthenticatedTenantAndWhatsappChannel() {
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        MessagingConversation conversation = conversation(conversationId, businessId);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(conversations.findAllByBusinessIdAndChannelOrderByLastMessageAtDesc(businessId, "whatsapp"))
                .thenReturn(List.of(conversation));

        MessagingConversationQueryService service = new MessagingConversationQueryService(conversations, messages, tenantProvider);

        var result = service.listWhatsApp();

        assertEquals(1, result.size());
        assertEquals(conversationId, result.getFirst().id());
        assertEquals("+56911111111", result.getFirst().sender());
        verify(conversations).findAllByBusinessIdAndChannelOrderByLastMessageAtDesc(businessId, "whatsapp");
        verifyNoInteractions(messages);
    }

    @Test
    void detailIsTenantScopedAndReturnsPersistedMessages() {
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        MessagingConversation conversation = conversation(conversationId, businessId);
        MessagingMessage message = new MessagingMessage();
        UUID messageId = UUID.randomUUID();
        ReflectionTestUtils.setField(message, "id", messageId);
        ReflectionTestUtils.setField(message, "createdAt", Instant.parse("2026-09-17T18:05:10Z"));
        message.setDirection("INBOUND");
        message.setRole("USER");
        message.setContent("Hola");

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(conversations.findByIdAndBusinessId(conversationId, businessId)).thenReturn(Optional.of(conversation));
        when(messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId)).thenReturn(List.of(message));

        MessagingConversationQueryService service = new MessagingConversationQueryService(conversations, messages, tenantProvider);

        var result = service.whatsappDetail(conversationId).orElseThrow();

        assertEquals(conversationId, result.conversation().id());
        assertEquals(1, result.messages().size());
        assertEquals("Hola", result.messages().getFirst().content());
        verify(conversations).findByIdAndBusinessId(conversationId, businessId);
        verify(messages).findAllByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Test
    void missingTenantConversationDoesNotReadMessages() {
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(conversations.findByIdAndBusinessId(conversationId, businessId)).thenReturn(Optional.empty());

        MessagingConversationQueryService service = new MessagingConversationQueryService(conversations, messages, tenantProvider);

        assertTrue(service.whatsappDetail(conversationId).isEmpty());
        verifyNoInteractions(messages);
    }

    private static MessagingConversation conversation(UUID id, UUID businessId) {
        MessagingConversation conversation = new MessagingConversation();
        ReflectionTestUtils.setField(conversation, "id", id);
        conversation.setBusinessId(businessId);
        conversation.setChannel("whatsapp");
        conversation.setSender("+56911111111");
        conversation.setRecipient("+56922222222");
        conversation.setOpenedAt(Instant.parse("2026-09-17T18:05:00Z"));
        conversation.setLastMessageAt(Instant.parse("2026-09-17T18:06:00Z"));
        return conversation;
    }
}
