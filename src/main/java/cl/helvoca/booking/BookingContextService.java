package cl.helvoca.booking;

import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallQueryService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.messaging.MessagingConversationQueryService;
import cl.helvoca.operations.BusinessOperationEventService;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BookingContextService {
    private final BookingRepository bookings;
    private final CallActionRepository actions;
    private final CallQueryService calls;
    private final MessagingConversationQueryService messaging;
    private final BusinessOperationEventService events;
    private final TenantProvider tenantProvider;

    public BookingContextService(BookingRepository bookings,
                                 CallActionRepository actions,
                                 CallQueryService calls,
                                 MessagingConversationQueryService messaging,
                                 BusinessOperationEventService events,
                                 TenantProvider tenantProvider) {
        this.bookings = bookings;
        this.actions = actions;
        this.calls = calls;
        this.messaging = messaging;
        this.events = events;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public BookingContextResponse get(UUID bookingId) {
        UUID businessId = tenantProvider.requireBusinessId();
        Booking booking = bookings.findByIdAndBusinessId(bookingId, businessId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        List<BusinessOperationEventService.EventView> history = booking.getOperationId() == null
                ? List.of()
                : events.recent(booking.getOperationId());

        return switch (booking.getSource()) {
            case AI_CALL -> voiceContext(businessId, bookingId, history);
            case AI_WHATSAPP -> whatsappContext(history);
            case ADMIN -> new BookingContextResponse("MANUAL", null, null, null, history);
        };
    }

    private BookingContextResponse voiceContext(UUID businessId,
                                                UUID bookingId,
                                                List<BusinessOperationEventService.EventView> history) {
        var action = actions.findTopByBusinessIdAndEntityTypeAndEntityIdAndSuccessTrueOrderByCreatedAtDesc(
                businessId, "BOOKING", bookingId);
        if (action.isEmpty()) {
            return new BookingContextResponse("VOICE", null, null, null, history);
        }
        UUID callId = action.get().getCallId();
        return new BookingContextResponse("VOICE", callId, calls.get(callId), null, history);
    }

    private BookingContextResponse whatsappContext(List<BusinessOperationEventService.EventView> history) {
        UUID conversationId = history.stream()
                .filter(event -> event.channel() == BusinessOrder.Source.WHATSAPP)
                .map(BusinessOperationEventService.EventView::sourceReferenceId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (conversationId == null) {
            return new BookingContextResponse("WHATSAPP", null, null, null, history);
        }
        return new BookingContextResponse(
                "WHATSAPP",
                conversationId,
                null,
                messaging.whatsappDetail(conversationId).orElse(null),
                history);
    }
}
