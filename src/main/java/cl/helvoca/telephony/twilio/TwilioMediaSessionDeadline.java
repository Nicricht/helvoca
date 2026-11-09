package cl.helvoca.telephony.twilio;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Optional absolute wall-clock limit on a Twilio-to-AI media session.
 * Disabled by default. A positive value is an explicit emergency override,
 * not the normal call-duration policy or a billing guarantee.
 */
@Component
final class TwilioMediaSessionDeadline {
    private final int seconds;
    private final ScheduledExecutorService scheduler;

    @Autowired
    TwilioMediaSessionDeadline(@Value("${app.telephony.twilio.max-media-session-seconds:0}")
                               int seconds) {
        this(seconds, seconds == 0 ? null : scheduler());
    }

    // Enables deterministic tests without waiting or contacting Twilio.
    TwilioMediaSessionDeadline(int seconds, ScheduledExecutorService scheduler) {
        if (seconds < 0 || (seconds > 0 && (seconds < 30 || seconds > 3600))) {
            throw new IllegalArgumentException("max-media-session-seconds must be 0 or 30..3600");
        }
        this.seconds = seconds;
        this.scheduler = seconds == 0 ? null : Objects.requireNonNull(scheduler, "deadline scheduler");
    }

    ScheduledFuture<?> schedule(Runnable cutOff) {
        Objects.requireNonNull(cutOff, "cutoff");
        if (seconds == 0) return null;
        return scheduler.schedule(cutOff, seconds, TimeUnit.SECONDS);
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
        if (scheduler != null) scheduler.shutdownNow();
    }
}
