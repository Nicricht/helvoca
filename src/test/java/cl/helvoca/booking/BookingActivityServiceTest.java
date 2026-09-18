package cl.helvoca.booking;

import cl.helvoca.audit.AuditLog;
import cl.helvoca.audit.AuditLogRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BookingActivityServiceTest {

    @Test
    void listsOnlyActivityForBookingInsideAuthenticatedTenant() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-18T18:00:00Z");

        BookingRepository bookings = mock(BookingRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditLog log = mock(AuditLog.class);

        Booking booking = new Booking();
        booking.setBusinessId(businessId);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(auditLogs.findByBusinessIdAndResourceTypeAndResourceIdOrderByCreatedAtAsc(
                businessId, "BOOKING", bookingId)).thenReturn(List.of(log));

        when(log.getId()).thenReturn(UUID.randomUUID());
        when(log.getAction()).thenReturn("BOOKING_RESCHEDULE");
        when(log.getActorType()).thenReturn("HUMAN");
        when(log.getActorUserId()).thenReturn(actorId);
        when(log.getActorName()).thenReturn("Carolina Soto");
        when(log.getActorRole()).thenReturn("OPERATOR");
        when(log.getBeforeState()).thenReturn(Map.of("startAt", "2026-09-18T15:00:00Z"));
        when(log.getAfterState()).thenReturn(Map.of("startAt", "2026-09-18T16:00:00Z"));
        when(log.getCreatedAt()).thenReturn(createdAt);

        List<BookingActivityResponse> result =
                new BookingActivityService(bookings, auditLogs, tenantProvider).list(bookingId);

        assertEquals(1, result.size());
        BookingActivityResponse activity = result.getFirst();
        assertEquals("BOOKING_RESCHEDULE", activity.action());
        assertEquals("Carolina Soto", activity.actorName());
        assertEquals("OPERATOR", activity.actorRole());
        assertEquals(actorId, activity.actorUserId());
        assertEquals("2026-09-18T15:00:00Z", activity.beforeState().get("startAt"));
        assertEquals("2026-09-18T16:00:00Z", activity.afterState().get("startAt"));
        assertEquals(createdAt, activity.createdAt());

        verify(auditLogs).findByBusinessIdAndResourceTypeAndResourceIdOrderByCreatedAtAsc(
                businessId, "BOOKING", bookingId);
    }

    @Test
    void doesNotExposeActivityWhenBookingDoesNotBelongToTenant() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        BookingRepository bookings = mock(BookingRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.empty());

        BookingActivityService service = new BookingActivityService(bookings, auditLogs, tenantProvider);

        assertThrows(NotFoundException.class, () -> service.list(bookingId));
        verifyNoInteractions(auditLogs);
    }
}
