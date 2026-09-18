package cl.helvoca.booking;

import cl.helvoca.audit.AuditLogRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BookingActivityService {
    private final BookingRepository bookings;
    private final AuditLogRepository auditLogs;
    private final TenantProvider tenantProvider;

    public BookingActivityService(
            BookingRepository bookings,
            AuditLogRepository auditLogs,
            TenantProvider tenantProvider) {
        this.bookings = bookings;
        this.auditLogs = auditLogs;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public List<BookingActivityResponse> list(UUID bookingId) {
        UUID businessId = tenantProvider.requireBusinessId();
        bookings.findByIdAndBusinessId(bookingId, businessId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));

        return auditLogs
                .findByBusinessIdAndResourceTypeAndResourceIdOrderByCreatedAtAsc(
                        businessId,
                        "BOOKING",
                        bookingId)
                .stream()
                .map(BookingActivityResponse::from)
                .toList();
    }
}
