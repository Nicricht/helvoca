package cl.helvoca.messaging.audio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class TranscriptionCircuitBreaker {
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final Duration cooldown;
    private final Clock clock;

    public TranscriptionCircuitBreaker(int failureThreshold, Duration cooldown) {
        this(failureThreshold, cooldown, Clock.systemUTC());
    }

    TranscriptionCircuitBreaker(int failureThreshold, Duration cooldown, Clock clock) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean allowRequest(String providerId) {
        String id = providerId(providerId);
        State state = states.get(id);
        if (state == null) return true;

        synchronized (state) {
            if (state.openUntil == null) return true;
            Instant now = clock.instant();
            if (now.isBefore(state.openUntil)) return false;
            if (state.halfOpenProbeInFlight) return false;
            state.halfOpenProbeInFlight = true;
            return true;
        }
    }

    public void onSuccess(String providerId) {
        states.remove(providerId(providerId));
    }

    public void onRetryableFailure(String providerId) {
        String id = providerId(providerId);
        State state = states.computeIfAbsent(id, ignored -> new State());
        synchronized (state) {
            if (state.halfOpenProbeInFlight) {
                open(state);
                return;
            }
            state.consecutiveFailures++;
            if (state.consecutiveFailures >= failureThreshold) {
                open(state);
            }
        }
    }

    public void onPermanentFailure(String providerId) {
        states.remove(providerId(providerId));
    }

    private void open(State state) {
        state.consecutiveFailures = failureThreshold;
        state.openUntil = clock.instant().plus(cooldown);
        state.halfOpenProbeInFlight = false;
    }

    private static String providerId(String value) {
        if (value == null || !value.matches("[a-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("Invalid audio provider id");
        }
        return value;
    }

    private static final class State {
        private int consecutiveFailures;
        private Instant openUntil;
        private boolean halfOpenProbeInFlight;
    }
}
