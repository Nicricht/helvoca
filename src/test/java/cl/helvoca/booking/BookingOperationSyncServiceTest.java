package cl.helvoca.booking;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationStateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingOperationSyncServiceTest {
    @Mock BookingRepository bookings;
    @Mock BusinessOperationRepository operations;
    @Mock ConversationStateService conversationState;

    @Test
    void voiceBookingIsEnrichedWithCallContextAndStructuredConversationState() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        Booking booking = booking(bookingId, operationId, businessId, serviceId);
        BusinessOperation operation = operation(operationId, businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(operations.saveAndFlush(any(BusinessOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessOperation saved = new BookingOperationSyncService(bookings, operations, conversationState)
                .synchronize(businessId, bookingId, callId, BusinessOrder.Source.VOICE, "create_booking");

        assertEquals(callId, saved.getSourceReferenceId());
        assertEquals(BusinessOrder.Source.VOICE, saved.getSource());
        assertEquals("create_booking", saved.getMetadata().get("lastTool"));
        assertEquals(bookingId.toString(), saved.getMetadata().get("bookingId"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> patch = ArgumentCaptor.forClass(Map.class);
        verify(conversationState).apply(eq(businessId), eq(callId), eq(BusinessOrder.Source.VOICE),
                eq(operationId), patch.capture());
        assertEquals("BOOKING", patch.getValue().get("intent"));
        assertEquals(bookingId.toString(), patch.getValue().get("bookingId"));
        assertEquals("CONFIRMED", patch.getValue().get("bookingStatus"));
        assertEquals(false, patch.getValue().get("confirmationPending"));
    }

    @Test
    void tenantScopedLookupPreventsCrossTenantSynchronization() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                new BookingOperationSyncService(bookings, operations, conversationState)
                        .synchronize(businessId, bookingId, UUID.randomUUID(),
                                BusinessOrder.Source.WHATSAPP, "reschedule_booking"));

        verifyNoInteractions(operations, conversationState);
    }

    @Test
    void nonBookingOperationFailsClosedBeforeConversationStateWrite() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Booking booking = booking(bookingId, operationId, businessId, UUID.randomUUID());
        BusinessOperation operation = operation(operationId, businessId);
        operation.setType(BusinessOperation.Type.ORDER);

        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        assertThrows(IllegalStateException.class, () ->
                new BookingOperationSyncService(bookings, operations, conversationState)
                        .synchronize(businessId, bookingId, UUID.randomUUID(),
                                BusinessOrder.Source.VOICE, "cancel_booking"));

        verify(operations, never()).saveAndFlush(any());
        verifyNoInteractions(conversationState);
    }

    private static Booking booking(UUID bookingId,
                                   UUID operationId,
                                   UUID businessId,
                                   UUID serviceId) {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", bookingId);
        booking.setOperationId(operationId);
        booking.setBusinessId(businessId);
        booking.setCustomerId(UUID.randomUUID());
        booking.setServiceId(serviceId);
        booking.setStartAt(Instant.parse("2030-01-01T12:00:00Z"));
        booking.setEndAt(Instant.parse("2030-01-01T13:00:00Z"));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSource(BookingSource.AI_CALL);
        return booking;
    }

    private static BusinessOperation operation(UUID operationId, UUID businessId) {
        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.BOOKING);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setSource(BusinessOrder.Source.VOICE);
        operation.setRevision(2);
        operation.setMetadata(new java.util.LinkedHashMap<>());
        return operation;
    }
}
