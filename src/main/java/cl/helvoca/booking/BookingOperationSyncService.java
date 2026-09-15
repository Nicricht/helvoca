package cl.helvoca.booking;

import cl.helvoca.calendar.CalendarSyncOutboxService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enriches the database-enforced BOOKING projection with channel context.
 * Availability, overlap, ownership and certification rules remain in the
 * existing booking flows; this service never creates or reschedules a booking.
 */
@Service
public class BookingOperationSyncService {
    private final BookingRepository bookings;
    private final BusinessOperationRepository operations;
    private final ConversationStateService conversationState;
    private final CalendarSyncOutboxService calendarSync;

    @Autowired
    public BookingOperationSyncService(BookingRepository bookings,
                                       BusinessOperationRepository operations,
                                       ConversationStateService conversationState,
                                       CalendarSyncOutboxService calendarSync) {
        this.bookings = bookings;
        this.operations = operations;
        this.conversationState = conversationState;
        this.calendarSync = calendarSync;
    }

    // Kept package-visible for focused unit tests that do not bootstrap calendar infrastructure.
    BookingOperationSyncService(BookingRepository bookings,
                                BusinessOperationRepository operations,
                                ConversationStateService conversationState) {
        this.bookings = bookings;
        this.operations = operations;
        this.conversationState = conversationState;
        this.calendarSync = null;
    }

    @Transactional
    public BusinessOperation synchronize(UUID businessId,
                                         UUID bookingId,
                                         UUID sourceReferenceId,
                                         BusinessOrder.Source source,
                                         String toolName) {
        if (businessId == null || bookingId == null) {
            throw new IllegalArgumentException("businessId and bookingId are required");
        }

        Booking booking = bookings.findByIdAndBusinessId(bookingId, businessId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (booking.getOperationId() == null) {
            throw new IllegalStateException("Booking is missing its universal operation");
        }

        BusinessOperation operation = operations
                .findByIdAndBusinessId(booking.getOperationId(), businessId)
                .orElseThrow(() -> new IllegalStateException("Booking operation projection is missing"));
        if (operation.getType() != BusinessOperation.Type.BOOKING) {
            throw new IllegalStateException("Booking points to a non-booking operation");
        }

        BusinessOrder.Source safeSource = source == null ? operation.getSource() : source;
        operation.setSourceReferenceId(sourceReferenceId);
        operation.setSource(safeSource);

        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        if (operation.getMetadata() != null) metadata.putAll(operation.getMetadata());
        metadata.put("intent", "BOOKING");
        metadata.put("bookingId", booking.getId().toString());
        metadata.put("serviceId", booking.getServiceId().toString());
        metadata.put("startAt", booking.getStartAt().toString());
        metadata.put("endAt", booking.getEndAt().toString());
        metadata.put("projectionStatus", booking.getStatus().name());
        if (toolName != null && !toolName.isBlank()) metadata.put("lastTool", toolName);
        if (booking.getNotes() != null && !booking.getNotes().isBlank()) metadata.put("notes", booking.getNotes());
        else metadata.remove("notes");
        operation.setMetadata(metadata);
        operation = operations.saveAndFlush(operation);

        if (sourceReferenceId != null) {
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("intent", "BOOKING");
            patch.put("operationId", operation.getId().toString());
            patch.put("operationType", "BOOKING");
            patch.put("operationStatus", operation.getStatus().name());
            patch.put("operationRevision", operation.getRevision());
            patch.put("bookingId", booking.getId().toString());
            patch.put("serviceId", booking.getServiceId().toString());
            patch.put("startAt", booking.getStartAt().toString());
            patch.put("endAt", booking.getEndAt().toString());
            patch.put("bookingStatus", booking.getStatus().name());
            patch.put("confirmationPending", false);
            if (toolName != null && !toolName.isBlank()) patch.put("lastTool", toolName);
            conversationState.apply(
                    businessId,
                    sourceReferenceId,
                    safeSource == null ? BusinessOrder.Source.API : safeSource,
                    operation.getId(),
                    patch);
        }

        if (calendarSync != null) {
            calendarSync.enqueueIfConnected(booking);
        }
        return operation;
    }
}
