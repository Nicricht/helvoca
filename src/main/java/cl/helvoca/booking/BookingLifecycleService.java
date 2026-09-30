package cl.helvoca.booking;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingLifecycleService {
    private final BookingRepository bookings;
    private final BusinessOperationRepository operations;
    private final TenantProvider tenant;
    private final AuditService audit;

    public BookingLifecycleService(BookingRepository bookings,
                                   BusinessOperationRepository operations,
                                   TenantProvider tenant,
                                   AuditService audit) {
        this.bookings = bookings;
        this.operations = operations;
        this.tenant = tenant;
        this.audit = audit;
    }

    @Transactional
    public BookingResponse complete(UUID bookingId) {
        return transition(bookingId, BookingStatus.COMPLETED, "BOOKING_COMPLETE");
    }

    @Transactional
    public BookingResponse noShow(UUID bookingId) {
        return transition(bookingId, BookingStatus.NO_SHOW, "BOOKING_NO_SHOW");
    }

    private BookingResponse transition(UUID bookingId, BookingStatus target, String auditAction) {
        UUID businessId = tenant.requireBusinessId();
        Booking booking = bookings.findByIdAndBusinessId(bookingId, businessId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));

        if (booking.getStatus() == target) return BookingResponse.from(booking);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Only confirmed bookings can change attendance outcome");
        }
        if (booking.getStartAt() == null || booking.getStartAt().isAfter(Instant.now())) {
            throw new ConflictException("Attendance outcome cannot be recorded before the booking starts");
        }

        Map<String, Object> before = snapshot(booking);
        booking.setStatus(target);
        bookings.saveAndFlush(booking);
        syncOperation(booking, target);

        audit.humanSuccess(
                businessId,
                auditAction,
                "BOOKING",
                bookingId,
                before,
                snapshot(booking));
        return BookingResponse.from(booking);
    }

    private void syncOperation(Booking booking, BookingStatus target) {
        if (booking.getOperationId() == null) return;
        BusinessOperation operation = operations
                .findByIdAndBusinessId(booking.getOperationId(), booking.getBusinessId())
                .orElse(null);
        if (operation == null || operation.getType() != BusinessOperation.Type.BOOKING) return;

        operation.setStatus(target == BookingStatus.COMPLETED
                ? BusinessOperation.Status.COMPLETED
                : BusinessOperation.Status.CANCELLED);
        operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (operation.getMetadata() != null) metadata.putAll(operation.getMetadata());
        metadata.put("projectionStatus", target.name());
        metadata.put("attendanceStatus", target.name());
        metadata.put("attendanceRecordedAt", Instant.now().toString());
        operation.setMetadata(metadata);
        operations.saveAndFlush(operation);
    }

    private static Map<String, Object> snapshot(Booking booking) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (booking.getId() != null) out.put("id", booking.getId().toString());
        if (booking.getOperationId() != null) out.put("operationId", booking.getOperationId().toString());
        if (booking.getStatus() != null) out.put("status", booking.getStatus().name());
        if (booking.getStartAt() != null) out.put("startAt", booking.getStartAt().toString());
        if (booking.getEndAt() != null) out.put("endAt", booking.getEndAt().toString());
        return out;
    }
}
