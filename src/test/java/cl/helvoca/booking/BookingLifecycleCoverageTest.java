package cl.helvoca.booking;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingLifecycleCoverageTest {

    @Test
    void terminalTransitionIsIdempotentAndRejectsDifferentTerminalOutcome() {
        Fixture completed = fixture(BookingStatus.COMPLETED, Instant.now().minusSeconds(60));
        BookingResponse replay = completed.service.complete(completed.bookingId);
        assertEquals(BookingStatus.COMPLETED, replay.status());
        verify(completed.bookings, never()).saveAndFlush(any());

        Fixture cancelled = fixture(BookingStatus.CANCELLED, Instant.now().minusSeconds(60));
        assertThrows(ConflictException.class, () -> cancelled.service.complete(cancelled.bookingId));
        verify(cancelled.bookings, never()).saveAndFlush(any());
    }

    @Test
    void missingBookingAndMissingStartAreRejected() {
        Fixture missing = fixture(BookingStatus.CONFIRMED, Instant.now().minusSeconds(60));
        when(missing.bookings.findByIdAndBusinessId(missing.bookingId, missing.businessId))
                .thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> missing.service.complete(missing.bookingId));

        Fixture noStart = fixture(BookingStatus.CONFIRMED, null);
        assertThrows(ConflictException.class, () -> noStart.service.noShow(noStart.bookingId));
        verify(noStart.operations, never()).saveAndFlush(any());
    }

    @Test
    void noShowCanCompleteWithoutProjectionAndWrongProjectionIsIgnored() {
        Fixture noProjectionId = fixture(BookingStatus.CONFIRMED, Instant.now().minusSeconds(60));
        noProjectionId.booking.setOperationId(null);
        noProjectionId.service.noShow(noProjectionId.bookingId);
        assertEquals(BookingStatus.NO_SHOW, noProjectionId.booking.getStatus());
        verify(noProjectionId.operations, never()).saveAndFlush(any());

        Fixture wrongProjection = fixture(BookingStatus.CONFIRMED, Instant.now().minusSeconds(60));
        wrongProjection.operation.setType(BusinessOperation.Type.ORDER);
        wrongProjection.service.noShow(wrongProjection.bookingId);
        assertEquals(BookingStatus.NO_SHOW, wrongProjection.booking.getStatus());
        verify(wrongProjection.operations, never()).saveAndFlush(any());

        Fixture missingProjection = fixture(BookingStatus.CONFIRMED, Instant.now().minusSeconds(60));
        when(missingProjection.operations.findByIdAndBusinessId(
                missingProjection.operationId, missingProjection.businessId)).thenReturn(Optional.empty());
        missingProjection.service.noShow(missingProjection.bookingId);
        assertEquals(BookingStatus.NO_SHOW, missingProjection.booking.getStatus());
        verify(missingProjection.operations, never()).saveAndFlush(any());
    }

    @Test
    void noShowMapsUniversalOperationToCancelledAndPreservesMetadata() {
        Fixture f = fixture(BookingStatus.CONFIRMED, Instant.now().minusSeconds(60));
        f.operation.setRevision(null);
        f.operation.setMetadata(new LinkedHashMap<>(Map.of("existing", "kept")));

        f.service.noShow(f.bookingId);

        assertEquals(BookingStatus.NO_SHOW, f.booking.getStatus());
        assertEquals(BusinessOperation.Status.CANCELLED, f.operation.getStatus());
        assertEquals(1, f.operation.getRevision());
        assertEquals("kept", f.operation.getMetadata().get("existing"));
        assertEquals("NO_SHOW", f.operation.getMetadata().get("attendanceStatus"));
        verify(f.operations).saveAndFlush(f.operation);
    }

    private static Fixture fixture(BookingStatus status, Instant startAt) {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BookingRepository bookings = mock(BookingRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        Booking booking = new Booking();
        booking.setBusinessId(businessId);
        booking.setOperationId(operationId);
        booking.setCustomerId(UUID.randomUUID());
        booking.setServiceId(UUID.randomUUID());
        booking.setStartAt(startAt);
        booking.setEndAt(startAt == null ? null : startAt.plusSeconds(1800));
        booking.setStatus(status);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.BOOKING);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(2);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        return new Fixture(
                businessId, bookingId, operationId, booking, operation,
                bookings, operations,
                new BookingLifecycleService(bookings, operations, tenant, audit));
    }

    private record Fixture(
            UUID businessId,
            UUID bookingId,
            UUID operationId,
            Booking booking,
            BusinessOperation operation,
            BookingRepository bookings,
            BusinessOperationRepository operations,
            BookingLifecycleService service) {}
}
