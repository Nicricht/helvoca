package cl.helvoca.telephony.twilio;

import cl.helvoca.operations.ControlledPilotExternalEffectGuard;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwilioPilotInFlightStopMonitorTest {
    private final UUID tenant = UUID.randomUUID();
    private ControlledPilotExternalEffectGuard guard;
    private TenantDatabaseContext context;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> future;
    private Runnable tick;
    private TwilioPilotInFlightStopMonitor monitor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        guard = mock(ControlledPilotExternalEffectGuard.class);
        context = new TenantDatabaseContext();
        scheduler = mock(ScheduledExecutorService.class);
        future = mock(ScheduledFuture.class);
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), eq(3L), eq(3L), eq(TimeUnit.SECONDS)))
                .thenAnswer(invocation -> {
                    tick = invocation.getArgument(0);
                    return future;
                });
        monitor = new TwilioPilotInFlightStopMonitor(guard, context, 3, scheduler);
    }

    @Test
    void armedPilotOrUnmanagedTenantDoNotStopActiveCall() {
        AtomicInteger stopped = new AtomicInteger();
        when(guard.evaluate(tenant, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(new ControlledPilotExternalEffectGuard.Decision(
                        true, true, "ALLOWED", "RUNNING", ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(new ControlledPilotExternalEffectGuard.Decision(
                        true, false, "NOT_PILOT_MANAGED", null, ControlledPilotExternalEffectGuard.Effect.VOICE));
        assertSame(future, monitor.watch(tenant, stopped::incrementAndGet));
        tick.run();
        tick.run();
        assertEquals(0, stopped.get());
        verify(guard, times(2)).evaluate(tenant, ControlledPilotExternalEffectGuard.Effect.VOICE);
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAUSED", "DRAFT", "READY", "COMPLETED", "GLOBAL_KILL_SWITCH_ACTIVE"})
    void stoppedPilotTriggersCarrierAndAiShutdown(String code) {
        AtomicInteger stopped = new AtomicInteger();
        when(guard.evaluate(tenant, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenAnswer(invocation -> {
                    assertEquals(TenantDatabaseContext.Mode.SYSTEM, context.currentOrDenied().mode());
                    return new ControlledPilotExternalEffectGuard.Decision(
                            false, true, code, code, ControlledPilotExternalEffectGuard.Effect.VOICE);
                });
        monitor.watch(tenant, stopped::incrementAndGet);
        tick.run();
        assertEquals(1, stopped.get());
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    @Test
    void unknownPilotControlFailsClosedInsteadOfLeavingCallAlive() {
        AtomicInteger stopped = new AtomicInteger();
        when(guard.evaluate(tenant, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenThrow(new IllegalStateException("Postgres control unavailable"));
        monitor.watch(tenant, stopped::incrementAndGet);
        tick.run();
        assertEquals(1, stopped.get());
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    @Test
    void failedShutdownCallbackDoesNotKillSchedulerThread() {
        AtomicInteger attempts = new AtomicInteger();
        when(guard.evaluate(tenant, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(new ControlledPilotExternalEffectGuard.Decision(
                        false, true, "PILOT_NOT_RUNNING", "PAUSED",
                        ControlledPilotExternalEffectGuard.Effect.VOICE));
        monitor.watch(tenant, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("carrier API timeout");
        });
        assertDoesNotThrow(tick::run);
        assertDoesNotThrow(tick::run);
        assertEquals(2, attempts.get());
    }

    @Test
    void checksAreScheduledWithBoundedDelayAndCanBeCancelledByOwner() {
        monitor.watch(tenant, () -> {});
        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(3L), eq(3L), eq(TimeUnit.SECONDS));
        assertNotNull(tick);
        monitor.shutdown();
        verify(scheduler).shutdownNow();
    }

    @Test
    void invalidWatcherParametersFailBeforeScheduling() {
        assertThrows(NullPointerException.class, () -> monitor.watch(null, () -> {}));
        assertThrows(NullPointerException.class, () -> monitor.watch(tenant, null));
        verify(scheduler, never()).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    }

    @Test
    void invalidIntervalsAndRequiredCollaboratorsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new TwilioPilotInFlightStopMonitor(guard, context, 0, scheduler));
        assertThrows(IllegalArgumentException.class,
                () -> new TwilioPilotInFlightStopMonitor(guard, context, 31, scheduler));
        assertThrows(NullPointerException.class,
                () -> new TwilioPilotInFlightStopMonitor(null, context, 3, scheduler));
        assertThrows(NullPointerException.class,
                () -> new TwilioPilotInFlightStopMonitor(guard, null, 3, scheduler));
        assertThrows(NullPointerException.class,
                () -> new TwilioPilotInFlightStopMonitor(guard, context, 3, null));
    }

    @Test
    void springConstructorOwnsDaemonSchedulerAndStopsOnShutdown() throws Exception {
        CountDownLatch checked = new CountDownLatch(1);
        when(guard.evaluate(tenant, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenAnswer(invocation -> {
                    assertTrue(Thread.currentThread().isDaemon());
                    checked.countDown();
                    return new ControlledPilotExternalEffectGuard.Decision(
                            true, false, "NOT_PILOT_MANAGED", null,
                            ControlledPilotExternalEffectGuard.Effect.VOICE);
                });
        TwilioPilotInFlightStopMonitor real = new TwilioPilotInFlightStopMonitor(guard, context, 1);
        try {
            ScheduledFuture<?> watched = real.watch(tenant, () -> fail("unmanaged call stopped"));
            assertTrue(checked.await(3, TimeUnit.SECONDS));
            watched.cancel(false);
        } finally {
            real.shutdown();
        }
    }
}
