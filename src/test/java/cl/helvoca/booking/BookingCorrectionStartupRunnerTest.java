package cl.helvoca.booking;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingCorrectionStartupRunnerTest {

    @Test
    void correctsTheSameBookingWithoutCreatingAnother() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant previous = Instant.parse("2026-09-24T12:30:00Z");
        Instant target = Instant.parse("2026-09-24T14:30:00Z");

        Booking existing = mock(Booking.class);
        when(existing.getId()).thenReturn(bookingId);
        when(existing.getStartAt()).thenReturn(previous);
        when(existing.getNotes()).thenReturn("nota original");

        BookingRepository bookings = mock(BookingRepository.class);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(existing));

        BookingService service = mock(BookingService.class);
        when(service.rescheduleForOperationalCorrection(
                eq(businessId),
                eq(bookingId),
                any(RescheduleBookingRequest.class)))
                .thenReturn(new BookingResponse(
                        bookingId,
                        customerId,
                        serviceId,
                        target,
                        target.plusSeconds(3600),
                        BookingStatus.CONFIRMED,
                        BookingSource.AI_WHATSAPP,
                        "nota original",
                        Instant.now(),
                        Instant.now()));

        BookingCorrectionStartupRunner runner = new BookingCorrectionStartupRunner(
                true,
                businessId.toString(),
                bookingId.toString(),
                target.toString(),
                mock(TenantDatabaseContext.class),
                bookings,
                service);

        BookingCorrectionStartupRunner.CorrectionResult result =
                runner.correct(businessId, bookingId, target);

        assertEquals(previous, result.previousStartAt());
        assertEquals(target, result.actualStartAt());
        assertFalse(result.idempotent());
        verify(service).rescheduleForOperationalCorrection(
                eq(businessId),
                eq(bookingId),
                argThat(request -> target.equals(request.startAt())
                        && "nota original".equals(request.notes())));
        verifyNoMoreInteractions(service);
    }

    @Test
    void isIdempotentWhenBookingAlreadyHasTargetTime() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant target = Instant.parse("2026-09-24T14:30:00Z");

        Booking existing = mock(Booking.class);
        when(existing.getStartAt()).thenReturn(target);

        BookingRepository bookings = mock(BookingRepository.class);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(existing));

        BookingService service = mock(BookingService.class);
        BookingCorrectionStartupRunner runner = new BookingCorrectionStartupRunner(
                true,
                businessId.toString(),
                bookingId.toString(),
                target.toString(),
                mock(TenantDatabaseContext.class),
                bookings,
                service);

        BookingCorrectionStartupRunner.CorrectionResult result =
                runner.correct(businessId, bookingId, target);

        assertTrue(result.idempotent());
        assertEquals(target, result.actualStartAt());
        verifyNoInteractions(service);
    }

    @Test
    void failsIfTheCorrectionReturnsAnotherBookingId() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant previous = Instant.parse("2026-09-24T12:30:00Z");
        Instant target = Instant.parse("2026-09-24T14:30:00Z");

        Booking existing = mock(Booking.class);
        when(existing.getStartAt()).thenReturn(previous);

        BookingRepository bookings = mock(BookingRepository.class);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(existing));

        BookingService service = mock(BookingService.class);
        when(service.rescheduleForOperationalCorrection(any(), any(), any()))
                .thenReturn(new BookingResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        target,
                        target.plusSeconds(3600),
                        BookingStatus.CONFIRMED,
                        BookingSource.AI_WHATSAPP,
                        null,
                        Instant.now(),
                        Instant.now()));

        BookingCorrectionStartupRunner runner = new BookingCorrectionStartupRunner(
                true,
                businessId.toString(),
                bookingId.toString(),
                target.toString(),
                mock(TenantDatabaseContext.class),
                bookings,
                service);

        assertThrows(
                IllegalStateException.class,
                () -> runner.correct(businessId, bookingId, target));
    }
}
