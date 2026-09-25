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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

        ZoneId zone = ZoneId.of("America/Santiago");
        LocalDate targetDate = LocalDate.now(zone).plusDays(2);
        Instant targetStart = targetDate.atTime(11, 30).atZone(zone).toInstant();
        Instant targetEnd = targetStart.plus(Duration.ofMinutes(30));
        Instant previousStart = targetStart.minus(Duration.ofHours(2));
        Instant previousEnd = previousStart.plus(Duration.ofMinutes(30));

        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getServiceId()).thenReturn(serviceId);
        when(booking.getOperationId()).thenReturn(operationId);
        when(booking.getStartAt()).thenReturn(previousStart);
        when(booking.getEndAt()).thenReturn(previousEnd);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(bookings.countOverlaps(
                eq(businessId),
                eq(serviceId),
                eq(targetStart),
                eq(targetEnd),
                eq(BookingStatus.CANCELLED),
                eq(bookingId))).thenReturn(0L);

        Business business = new Business();
        business.setTimezone("America/Santiago");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(schedule.isWithinBusinessHours(
                businessId,
                targetStart,
                targetEnd)).thenReturn(true);

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
                previousStart.toString(),
                targetDate.toString(),
                "11:30");

        assertEquals("REPAIRED", result.status());
        assertEquals(previousStart, result.previousStartAt());
        assertEquals(targetStart, result.targetStartAt());
        assertEquals("America/Santiago", result.timezone());

        verify(booking).setStartAt(targetStart);
        verify(booking).setEndAt(targetEnd);
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

        ZoneId zone = ZoneId.of("America/Santiago");
        LocalDate targetDate = LocalDate.now(zone).plusDays(2);
        Instant expectedCurrentStart = targetDate.atTime(9, 30).atZone(zone).toInstant();
        Instant actualCurrentStart = expectedCurrentStart.plus(Duration.ofHours(1));

        Booking booking = mock(Booking.class);
        when(booking.getStartAt()).thenReturn(actualCurrentStart);
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
                expectedCurrentStart.toString(),
                targetDate.toString(),
                "11:30");

        assertEquals("SKIPPED_UNEXPECTED_CURRENT_START", result.status());
        verify(bookings, never()).saveAndFlush(any());
    }
}
