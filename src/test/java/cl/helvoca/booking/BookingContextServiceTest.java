package cl.helvoca.booking;

import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallDetailResponse;
import cl.helvoca.call.CallQueryService;
import cl.helvoca.messaging.MessagingConversationQueryService;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationEvent;
import cl.helvoca.operations.BusinessOperationEventService;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingContextServiceTest {

    @Test
    void voiceBookingUsesCallActionEntityLink() {
        Fixture f = new Fixture(BookingSource.AI_CALL);
        UUID callId = UUID.randomUUID();
        CallAction action = new CallAction();
        action.setBusinessId(f.businessId);
        action.setCallId(callId);
        action.setActionType("BOOKING_CREATED");
        action.setSuccess(true);
        action.setEntityType("BOOKING");
        action.setEntityId(f.bookingId);

        CallDetailResponse detail = new CallDetailResponse(null, List.of(), "Reserva creada por llamada.", List.of());
        when(f.actions.findTopByBusinessIdAndEntityTypeAndEntityIdAndSuccessTrueOrderByCreatedAtDesc(
                f.businessId, "BOOKING", f.bookingId)).thenReturn(Optional.of(action));
        when(f.calls.get(callId)).thenReturn(detail);

        BookingContextResponse result = f.service().get(f.bookingId);

        assertEquals("VOICE", result.channel());
        assertEquals(callId, result.sourceReferenceId());
        assertSame(detail, result.call());
        assertNull(result.whatsapp());
    }

    @Test
    void whatsappBookingUsesOperationEventSourceReference() {
        Fixture f = new Fixture(BookingSource.AI_WHATSAPP);
        UUID conversationId = UUID.randomUUID();
        var event = new BusinessOperationEventService.EventView(
                UUID.randomUUID(), 1L, f.operationId, BusinessOperation.Type.BOOKING,
                "BOOKING_CREATED", BusinessOrder.Source.WHATSAPP, conversationId,
                1, BusinessOperation.Status.CONFIRMED, null, BusinessOperationEvent.ActorType.AI,
                Map.of(), Instant.now());
        when(f.events.recent(f.operationId)).thenReturn(List.of(event));

        var item = new MessagingConversationQueryService.ConversationItem(
                conversationId, null, "whatsapp", "+56911111111", "+56922222222",
                Instant.now(), Instant.now());
        var detail = new MessagingConversationQueryService.ConversationDetail(item, List.of());
        when(f.messaging.whatsappDetail(conversationId)).thenReturn(Optional.of(detail));

        BookingContextResponse result = f.service().get(f.bookingId);

        assertEquals("WHATSAPP", result.channel());
        assertEquals(conversationId, result.sourceReferenceId());
        assertSame(detail, result.whatsapp());
        assertNull(result.call());
    }

    @Test
    void manualBookingNeverInventsConversation() {
        Fixture f = new Fixture(BookingSource.ADMIN);

        BookingContextResponse result = f.service().get(f.bookingId);

        assertEquals("MANUAL", result.channel());
        assertNull(result.sourceReferenceId());
        assertNull(result.call());
        assertNull(result.whatsapp());
    }

    private static final class Fixture {
        final UUID businessId = UUID.randomUUID();
        final UUID bookingId = UUID.randomUUID();
        final UUID operationId = UUID.randomUUID();
        final BookingRepository bookings = mock(BookingRepository.class);
        final CallActionRepository actions = mock(CallActionRepository.class);
        final CallQueryService calls = mock(CallQueryService.class);
        final MessagingConversationQueryService messaging = mock(MessagingConversationQueryService.class);
        final BusinessOperationEventService events = mock(BusinessOperationEventService.class);
        final TenantProvider tenantProvider = mock(TenantProvider.class);

        Fixture(BookingSource source) {
            Booking booking = new Booking();
            booking.setBusinessId(businessId);
            booking.setOperationId(operationId);
            booking.setSource(source);
            when(tenantProvider.requireBusinessId()).thenReturn(businessId);
            when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
            when(events.recent(operationId)).thenReturn(List.of());
            when(actions.findTopByBusinessIdAndEntityTypeAndEntityIdAndSuccessTrueOrderByCreatedAtDesc(
                    businessId, "BOOKING", bookingId)).thenReturn(Optional.empty());
        }

        BookingContextService service() {
            return new BookingContextService(bookings, actions, calls, messaging, events, tenantProvider);
        }
    }
}
