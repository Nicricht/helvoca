package cl.helvoca.booking;

import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.operations.BusinessOperationEvent;
import cl.helvoca.operations.BusinessOperationEventRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class BookingTraceService {
    private final BookingRepository bookings;
    private final CallActionRepository actions;
    private final BusinessOperationEventRepository events;
    private final TenantProvider tenantProvider;

    public BookingTraceService(BookingRepository bookings,
                               CallActionRepository actions,
                               BusinessOperationEventRepository events,
                               TenantProvider tenantProvider) {
        this.bookings = bookings;
        this.actions = actions;
        this.events = events;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public TraceView trace(UUID bookingId) {
        UUID businessId = tenantProvider.requireBusinessId();
        Booking booking = bookings.findByIdAndBusinessId(bookingId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("La reserva no pertenece al negocio."));

        List<BusinessOperationEvent> operationEvents = booking.getOperationId() == null
                ? List.of()
                : events.findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(
                        businessId, booking.getOperationId());

        UUID callId = actions
                .findFirstByBusinessIdAndEntityTypeAndEntityIdAndSuccessTrueOrderByCreatedAtAsc(
                        businessId, "BOOKING", bookingId)
                .map(CallAction::getCallId)
                .orElseGet(() -> operationEvents.stream()
                        .filter(event -> event.getChannel() == BusinessOrder.Source.VOICE)
                        .map(BusinessOperationEvent::getSourceReferenceId)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(null));

        UUID conversationId = operationEvents.stream()
                .filter(event -> event.getChannel() == BusinessOrder.Source.WHATSAPP)
                .map(BusinessOperationEvent::getSourceReferenceId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        String origin;
        if (callId != null) origin = "VOICE";
        else if (conversationId != null) origin = "WHATSAPP";
        else if (booking.getSource() == BookingSource.AI_CALL) origin = "VOICE";
        else if (booking.getSource() == BookingSource.AI_WHATSAPP) origin = "WHATSAPP";
        else origin = "MANUAL";

        List<HistoryEvent> history = operationEvents.stream()
                .sorted(Comparator.comparing(
                        BusinessOperationEvent::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(event -> new HistoryEvent(
                        event.getEventType(),
                        event.getStatus() == null ? null : event.getStatus().name(),
                        event.getChannel() == null ? null : event.getChannel().name(),
                        event.getCreatedAt()))
                .toList();

        return new TraceView(
                booking.getId(),
                booking.getOperationId(),
                origin,
                callId,
                conversationId,
                history);
    }

    public record TraceView(
            UUID bookingId,
            UUID operationId,
            String origin,
            UUID callId,
            UUID conversationId,
            List<HistoryEvent> history) { }

    public record HistoryEvent(
            String eventType,
            String status,
            String channel,
            Instant createdAt) { }
}
