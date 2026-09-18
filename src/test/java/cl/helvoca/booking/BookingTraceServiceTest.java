package cl.helvoca.booking;

import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationEvent;
import cl.helvoca.operations.BusinessOperationEventRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingTraceServiceTest {

    @Test
    void voiceTraceUsesTenantScopedBookingAndCallAction() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        BookingRepository bookings = mock(BookingRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BusinessOperationEventRepository events = mock(BusinessOperationEventRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        Booking booking = mock(Booking.class);
        CallAction action = mock(CallAction.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getOperationId()).thenReturn(operationId);
        when(booking.getSource()).thenReturn(BookingSource.AI_CALL);
        when(actions.findFirstByBusinessIdAndEntityTypeAndEntityIdAndSuccessTrueOrderByCreatedAtAsc(
                businessId, "BOOKING", bookingId)).thenReturn(Optional.of(action));
        when(action.getCallId()).thenReturn(callId);
        when(events.findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(businessId, operationId))
                .thenReturn(List.of());

        BookingTraceService service = new BookingTraceService(bookings, actions, events, tenant);
        BookingTraceService.TraceView view = service.trace(bookingId);

        assertEquals("VOICE", view.origin());
        assertEquals(callId, view.callId());
        assertNull(view.conversationId());
        verify(bookings).findByIdAndBusinessId(bookingId, businessId);
    }

    @Test
    void whatsappTraceUsesOperationEventSourceReference() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        BookingRepository bookings = mock(BookingRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BusinessOperationEventRepository events = mock(BusinessOperationEventRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        Booking booking = mock(Booking.class);
        BusinessOperationEvent event = mock(BusinessOperationEvent.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getOperationId()).thenReturn(operationId);
        when(booking.getSource()).thenReturn(BookingSource.AI_WHATSAPP);
        when(actions.findFirstByBusinessIdAndEntityTypeAndEntityIdAndSuccessTrueOrderByCreatedAtAsc(
                businessId, "BOOKING", bookingId)).thenReturn(Optional.empty());
        when(event.getChannel()).thenReturn(BusinessOrder.Source.WHATSAPP);
        when(event.getSourceReferenceId()).thenReturn(conversationId);
        when(event.getEventType()).thenReturn("BOOKING_CREATED");
        when(event.getStatus()).thenReturn(BusinessOperation.Status.CONFIRMED);
        when(event.getCreatedAt()).thenReturn(Instant.parse("2026-09-17T20:00:00Z"));
        when(events.findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(businessId, operationId))
                .thenReturn(List.of(event));

        BookingTraceService service = new BookingTraceService(bookings, actions, events, tenant);
        BookingTraceService.TraceView view = service.trace(bookingId);

        assertEquals("WHATSAPP", view.origin());
        assertNull(view.callId());
        assertEquals(conversationId, view.conversationId());
        assertEquals(1, view.history().size());
        assertEquals("BOOKING_CREATED", view.history().get(0).eventType());
    }

    @Test
    void manualTraceNeverInventsConversation() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        BookingRepository bookings = mock(BookingRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BusinessOperationEventRepository events = mock(BusinessOperationEventRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        Booking booking = mock(Booking.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getOperationId()).thenReturn(operationId);
        when(booking.getSource()).thenReturn(BookingSource.ADMIN);
        when(actions.findFirstByBusinessIdAndEntityTypeAndEntityIdAndSuccessTrueOrderByCreatedAtAsc(
                businessId, "BOOKING", bookingId)).thenReturn(Optional.empty());
        when(events.findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(businessId, operationId))
                .thenReturn(List.of());

        BookingTraceService service = new BookingTraceService(bookings, actions, events, tenant);
        BookingTraceService.TraceView view = service.trace(bookingId);

        assertEquals("MANUAL", view.origin());
        assertNull(view.callId());
        assertNull(view.conversationId());
    }
}
