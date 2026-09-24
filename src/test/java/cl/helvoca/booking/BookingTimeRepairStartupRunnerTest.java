package cl.helvoca.booking;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BookingTimeRepairStartupRunnerTest {

    @Test
    void repairsOnlyTheExpectedBookingAndSynchronizesItsOperation() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        BookingRepository bookings = mock(BookingRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BookingOperationSyncService operationSync = mock(BookingOperationSyncService.class);

        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getServiceId()).thenReturn(serviceId);
        when(booking.getOperationId()).thenReturn(operationId);
        when(booking.getStartAt()).thenReturn(Instant.parse("2026-09-24T12:30:00Z"));
        when(booking.getEndAt()).thenReturn(Instant.parse("2026-09-24T13:00:00Z"));
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(bookings.countOverlaps(
                eq(businessId),
                eq(serviceId),
                eq(Instant.parse("2026-09-24T14:30:00Z")),
                eq(Instant.parse("2026-09-24T15:00:00Z")),
                eq(BookingStatus.CANCELLED),
                eq(bookingId))).thenReturn(0L);

        Business business = new Business();
        business.setTimezone("America/Santiago");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(schedule.isWithinBusinessHours(
                businessId,
                Instant.parse("2026-09-24T14:30:00Z"),
                Instant.parse("2026-09-24T15:00:00Z"))).thenReturn(true);

        BusinessOperation operation = mock(BusinessOperation.class);
        when(operation.getSourceReferenceId()).thenReturn(conversationId);
        when(operation.getSource()).thenReturn(BusinessOrder.Source.WHATSAPP);
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        BookingTimeRepairStartupRunner runner = new BookingTimeRepairStartupRunner(
                false, "", "", "", "", "",
                mock(TenantDatabaseContext.class),
                mock(PlatformTransactionManager.class),
                bookings,
                businesses,
                schedule,
                operations,
                operationSync);

        BookingTimeRepairStartupRunner.RepairResult result = runner.repair(
                businessId,
                bookingId,
                "2026-09-24T12:30:00Z",
                "2026-09-24",
                "11:30");

        assertEquals("REPAIRED", result.status());
        assertEquals(Instant.parse("2026-09-24T12:30:00Z"), result.previousStartAt());
        assertEquals(Instant.parse("2026-09-24T14:30:00Z"), result.targetStartAt());
        assertEquals("America/Santiago", result.timezone());

        verify(booking).setStartAt(Instant.parse("2026-09-24T14:30:00Z"));
        verify(booking).setEndAt(Instant.parse("2026-09-24T15:00:00Z"));
        verify(bookings).saveAndFlush(booking);
        verify(operationSync).synchronize(
                businessId,
                bookingId,
                conversationId,
                BusinessOrder.Source.WHATSAPP,
                "reschedule_booking");
    }

    @Test
    void skipsMutationWhenTheBookingHasAlreadyChangedSinceDiagnosis() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        BookingRepository bookings = mock(BookingRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);

        Booking booking = mock(Booking.class);
        when(booking.getStartAt()).thenReturn(Instant.parse("2026-09-24T13:30:00Z"));
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));

        Business business = new Business();
        business.setTimezone("America/Santiago");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        BookingTimeRepairStartupRunner runner = new BookingTimeRepairStartupRunner(
                false, "", "", "", "", "",
                mock(TenantDatabaseContext.class),
                mock(PlatformTransactionManager.class),
                bookings,
                businesses,
                mock(BusinessScheduleService.class),
                mock(BusinessOperationRepository.class),
                mock(BookingOperationSyncService.class));

        BookingTimeRepairStartupRunner.RepairResult result = runner.repair(
                businessId,
                bookingId,
                "2026-09-24T12:30:00Z",
                "2026-09-24",
                "11:30");

        assertEquals("SKIPPED_UNEXPECTED_CURRENT_START", result.status());
        verify(bookings, never()).saveAndFlush(any());
    }
}
