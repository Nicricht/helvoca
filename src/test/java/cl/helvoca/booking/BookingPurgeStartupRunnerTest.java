package cl.helvoca.booking;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BookingPurgeStartupRunnerTest {

    @Test
    void purgesEveryBookingForTheSelectedBusiness() {
        UUID businessId = UUID.randomUUID();
        Booking first = mock(Booking.class);
        Booking second = mock(Booking.class);
        BookingRepository bookings = mock(BookingRepository.class);

        when(bookings.findAllByBusinessIdOrderByStartAtDesc(businessId))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of());

        BookingPurgeStartupRunner runner = new BookingPurgeStartupRunner(
                true,
                businessId.toString(),
                mock(TenantDatabaseContext.class),
                mock(PlatformTransactionManager.class),
                bookings);

        BookingPurgeStartupRunner.PurgeResult result = runner.purge(businessId);

        assertEquals(2, result.deleted());
        assertEquals(0, result.remaining());
        verify(bookings).deleteAllInBatch(List.of(first, second));
        verify(bookings).flush();
    }

    @Test
    void succeedsWhenTheBusinessAlreadyHasNoBookings() {
        UUID businessId = UUID.randomUUID();
        BookingRepository bookings = mock(BookingRepository.class);
        when(bookings.findAllByBusinessIdOrderByStartAtDesc(businessId)).thenReturn(List.of());

        BookingPurgeStartupRunner runner = new BookingPurgeStartupRunner(
                true,
                businessId.toString(),
                mock(TenantDatabaseContext.class),
                mock(PlatformTransactionManager.class),
                bookings);

        BookingPurgeStartupRunner.PurgeResult result = runner.purge(businessId);

        assertEquals(0, result.deleted());
        assertEquals(0, result.remaining());
        verify(bookings, never()).deleteAllInBatch(anyList());
    }
}
