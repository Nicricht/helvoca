package cl.helvoca.booking;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingLifecycleServiceTest {
    @Test
    void completingPastConfirmedBookingRecordsTerminalOutcomeAndUniversalState() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BookingRepository bookings = mock(BookingRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getBusinessId()).thenReturn(businessId);
        when(booking.getOperationId()).thenReturn(operationId);
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);
        when(booking.getStartAt()).thenReturn(Instant.now().minusSeconds(3600));

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.BOOKING);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(1);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        BookingLifecycleService service = new BookingLifecycleService(bookings, operations, tenant, audit);
        service.complete(bookingId);

        verify(booking).setStatus(BookingStatus.COMPLETED);
        assertEquals(BusinessOperation.Status.COMPLETED, operation.getStatus());
        assertEquals("COMPLETED", operation.getMetadata().get("attendanceStatus"));
        verify(operations).saveAndFlush(operation);
        verify(audit).humanSuccess(eq(businessId), eq("BOOKING_COMPLETE"), eq("BOOKING"),
                eq(bookingId), anyMap(), anyMap());
    }

    @Test
    void futureBookingCannotBeMarkedNoShow() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingRepository bookings = mock(BookingRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        Booking booking = mock(Booking.class);
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(booking.getStartAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));

        BookingLifecycleService service = new BookingLifecycleService(bookings, operations, tenant, audit);
        assertThrows(ConflictException.class, () -> service.noShow(bookingId));
        verify(booking, never()).setStatus(any());
        verifyNoInteractions(operations);
    }
}
