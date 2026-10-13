package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwilioMediaSessionDeadlineTest {

    @Test
    void schedulesAtConfiguredBoundWithoutExecutingEarly() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(scheduler.schedule(any(Runnable.class), eq(600L), eq(TimeUnit.SECONDS)))
                .thenReturn(future);
        TwilioMediaSessionDeadline deadline = new TwilioMediaSessionDeadline(600, scheduler);
        AtomicInteger invoked = new AtomicInteger();
        assertSame(future, deadline.schedule(invoked::incrementAndGet));
        assertEquals(0, invoked.get());
        verify(scheduler).schedule(any(Runnable.class), eq(600L), eq(TimeUnit.SECONDS));
        deadline.stop();
        verify(scheduler).shutdownNow();
    }

    @Test
    void acceptsMinAndMaxButRejectsUnsafeOrUnlimitedValues() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        assertDoesNotThrow(() -> new TwilioMediaSessionDeadline(30, scheduler));
        assertDoesNotThrow(() -> new TwilioMediaSessionDeadline(3600, scheduler));
        for (int invalid : new int[]{-1, 0, 1, 29, 3601, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new TwilioMediaSessionDeadline(invalid, scheduler));
        }
        verifyNoInteractions(scheduler);
    }

    @Test
    void rejectsNullSchedulerAndNullCallbackWithoutSideEffects() {
        assertThrows(NullPointerException.class,
                () -> new TwilioMediaSessionDeadline(60, null));
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        TwilioMediaSessionDeadline deadline = new TwilioMediaSessionDeadline(60, scheduler);
        assertThrows(NullPointerException.class, () -> deadline.schedule(null));
        verifyNoInteractions(scheduler);
    }
}
