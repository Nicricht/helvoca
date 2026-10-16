package cl.helvoca.telephony.twilio;

import cl.helvoca.operations.ControlledPilotExternalEffectGuard;
import cl.helvoca.security.TenantDatabaseContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Instance-local watcher: every active media socket is checked by its owning
 * application instance, including while the caller sends no audio frames.
 * No Twilio request is made for healthy sessions. A STOP request is not
 * evidence that the carrier call has reached a terminal state.
 */
@Component
final class TwilioPilotInFlightStopMonitor {
    private static final Logger log = LoggerFactory.getLogger(TwilioPilotInFlightStopMonitor.class);

    private final ControlledPilotExternalEffectGuard guard;
    private final TenantDatabaseContext databaseContext;
    private final ScheduledExecutorService scheduler;
    private final int checkSeconds;

    @Autowired
    TwilioPilotInFlightStopMonitor(
            ControlledPilotExternalEffectGuard guard,
            TenantDatabaseContext databaseContext,
            @Value("${app.telephony.twilio.pilot-inflight-check-seconds:3}") int checkSeconds) {
        this(guard, databaseContext, checkSeconds, scheduler());
    }

    // Injectable scheduler makes STOP and carrier races deterministic in tests.
    TwilioPilotInFlightStopMonitor(
            ControlledPilotExternalEffectGuard guard,
            TenantDatabaseContext databaseContext,
            int checkSeconds,
            ScheduledExecutorService scheduler) {
        if (checkSeconds < 1 || checkSeconds > 30) {
            throw new IllegalArgumentException("pilot-inflight-check-seconds must be 1..30");
        }
        this.guard = Objects.requireNonNull(guard, "pilot guard");
        this.databaseContext = Objects.requireNonNull(databaseContext, "tenant database context");
        this.scheduler = Objects.requireNonNull(scheduler, "pilot STOP scheduler");
        this.checkSeconds = checkSeconds;
    }

    ScheduledFuture<?> watch(UUID trustedBusinessId, Runnable closeActiveCall) {
        Objects.requireNonNull(trustedBusinessId, "verified call tenant");
        Objects.requireNonNull(closeActiveCall, "STOP callback");
        return scheduler.scheduleWithFixedDelay(() -> {
            boolean allowed;
            try {
                allowed = databaseContext.callAsSystem(() -> guard.evaluate(
                        trustedBusinessId, ControlledPilotExternalEffectGuard.Effect.VOICE).allowed());
            } catch (RuntimeException e) {
                // Unknown control state must never keep an AI call alive.
                log.warn("PILOT_INFLIGHT_READ_FAILED tenant={}", trustedBusinessId);
                allowed = false;
            }
            if (!allowed) {
                try {
                    closeActiveCall.run();
                } catch (RuntimeException e) {
                    log.error("PILOT_INFLIGHT_LOCAL_STOP_FAILED tenant={}", trustedBusinessId);
                }
            }
        }, checkSeconds, checkSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    private static ScheduledExecutorService scheduler() {
        var executor = new ScheduledThreadPoolExecutor(2, task -> {
            Thread thread = new Thread(task, "helvoca-pilot-voice-stop");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }
}
