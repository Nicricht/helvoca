package cl.helvoca.messaging;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationOperationState;
import cl.helvoca.operations.ConversationStateService;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppBookingOperationStartupRunnerTest {

    @Test
    void resolvesLatestOperationAndDetectsMissingBookingProjection() {
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        TenantDatabaseContext databaseContext = mock(TenantDatabaseContext.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        ConversationStateService conversationState = mock(ConversationStateService.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        MessagingConversation conversation = mock(MessagingConversation.class);

        ConversationOperationState state = new ConversationOperationState();
        state.setState(Map.of(
                "operationId", operationId.toString(),
                "intent", "BOOKING",
                "confirmationPending", false));

        BusinessOperation operation = new BusinessOperation();
        operation.setType(BusinessOperation.Type.BOOKING);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);

        when(conversation.getId()).thenReturn(conversationId);
        when(conversations.findAllByBusinessIdAndChannelOrderByLastMessageAtDesc(businessId, "whatsapp"))
                .thenReturn(List.of(conversation));
        when(conversationState.find(businessId, conversationId, BusinessOrder.Source.WHATSAPP))
                .thenReturn(state);
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(bookings.findByOperationIdAndBusinessId(operationId, businessId)).thenReturn(Optional.empty());

        WhatsAppBookingOperationStartupRunner runner = new WhatsAppBookingOperationStartupRunner(
                true,
                businessId.toString(),
                databaseContext,
                conversations,
                conversationState,
                operations,
                bookings);

        WhatsAppBookingOperationStartupRunner.Diagnostic result = runner.evaluate(businessId);

        assertTrue(result.conversationFound());
        assertTrue(result.operationReferenceFound());
        assertTrue(result.operationFound());
        assertEquals("BOOKING", result.operationType());
        assertEquals("CONFIRMED", result.operationStatus());
        assertFalse(result.bookingProjectionFound());
        assertEquals("BOOKING", result.intent());
    }
}
