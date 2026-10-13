package cl.helvoca.telephony.twilio;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Hard, wall-clock limit on the Twilio-to-AI media session. Not a billing
 * guarantee: carrier billing can continue during a delayed hangup, and Twilio
 * provider-side limits and alerts must be verified separately.
 */
@Component
final class TwilioMediaSessionDeadline {
    private final int seconds;
    private final ScheduledExecutorService scheduler;

    TwilioMediaSessionDeadline(@Value("${app.telephony.twilio.max-media-session-seconds:600}")
                               int seconds) {
        this(seconds, scheduler());
    }

    // Enables deterministic tests without waiting or contacting Twilio.
    TwilioMediaSessionDeadline(int seconds, ScheduledExecutorService scheduler) {
        if (seconds < 30 || seconds > 3600) {
            throw new IllegalArgumentException("max-media-session-seconds must be 30..3600");
        }
        this.seconds = seconds;
        this.scheduler = Objects.requireNonNull(scheduler, "deadline scheduler");
    }

    ScheduledFuture<?> schedule(Runnable cutOff) {
        return scheduler.schedule(Objects.requireNonNull(cutOff, "cutoff"),
                seconds, TimeUnit.SECONDS);
    }

    private static ScheduledExecutorService scheduler() {
        var executor = new ScheduledThreadPoolExecutor(1, command -> {
            Thread thread = new Thread(command, "helvoca-voice-session-deadline");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }
}
