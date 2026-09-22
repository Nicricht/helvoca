package cl.helvoca.messaging;

import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationOperationState;
import cl.helvoca.operations.ConversationStateService;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppBookingFlowStartupRunnerTest {

    @Test
    void reportsPendingBookingProposalFromLatestWhatsAppConversationState() {
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        TenantDatabaseContext databaseContext = mock(TenantDatabaseContext.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        ConversationStateService conversationState = mock(ConversationStateService.class);
        MessagingConversation conversation = mock(MessagingConversation.class);
        ConversationOperationState state = new ConversationOperationState();
        state.setState(Map.of(
                "intent", "BOOKING",
                "operationStatus", "AWAITING_CONFIRMATION",
                "confirmationPending", true,
                "operationId", UUID.randomUUID().toString(),
                "confirmationToken", UUID.randomUUID().toString()));

        when(conversation.getId()).thenReturn(conversationId);
        when(conversations.findAllByBusinessIdAndChannelOrderByLastMessageAtDesc(businessId, "whatsapp"))
                .thenReturn(List.of(conversation));
        when(conversationState.find(businessId, conversationId, BusinessOrder.Source.WHATSAPP))
                .thenReturn(state);

        WhatsAppBookingFlowStartupRunner runner = new WhatsAppBookingFlowStartupRunner(
                true,
                businessId.toString(),
                databaseContext,
                conversations,
                conversationState);

        WhatsAppBookingFlowStartupRunner.Diagnostic result = runner.evaluate(businessId);

        assertTrue(result.conversationFound());
        assertTrue(result.stateFound());
        assertTrue(result.confirmationPending());
        assertTrue(result.hasOperationId());
        assertTrue(result.hasConfirmationToken());
        assertFalse(result.hasBookingId());
        assertEquals("AWAITING_CONFIRMATION", result.operationStatus());
    }
}
