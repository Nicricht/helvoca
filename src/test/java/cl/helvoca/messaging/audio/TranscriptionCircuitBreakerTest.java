package cl.helvoca.messaging.audio;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptionCircuitBreakerTest {

    @Test
    void opensAfterThreeRetryableFailures() {
        MutableClock clock = new MutableClock();
        TranscriptionCircuitBreaker breaker = new TranscriptionCircuitBreaker(3, Duration.ofSeconds(60), clock);

        breaker.onRetryableFailure("deepgram");
        breaker.onRetryableFailure("deepgram");
        assertTrue(breaker.allowRequest("deepgram"));

        breaker.onRetryableFailure("deepgram");

        assertFalse(breaker.allowRequest("deepgram"));
    }

    @Test
    void cooldownAllowsOneHalfOpenProbeAndSuccessClosesCircuit() {
        MutableClock clock = new MutableClock();
        TranscriptionCircuitBreaker breaker = new TranscriptionCircuitBreaker(3, Duration.ofSeconds(60), clock);
        breaker.onRetryableFailure("gemini");
        breaker.onRetryableFailure("gemini");
        breaker.onRetryableFailure("gemini");
        assertFalse(breaker.allowRequest("gemini"));

        clock.advance(Duration.ofSeconds(61));

        assertTrue(breaker.allowRequest("gemini"));
        assertFalse(breaker.allowRequest("gemini"));

        breaker.onSuccess("gemini");

        assertTrue(breaker.allowRequest("gemini"));
    }

    @Test
    void failedHalfOpenProbeReopensCooldown() {
        MutableClock clock = new MutableClock();
        TranscriptionCircuitBreaker breaker = new TranscriptionCircuitBreaker(1, Duration.ofSeconds(60), clock);
        breaker.onRetryableFailure("openai");
        clock.advance(Duration.ofSeconds(61));
        assertTrue(breaker.allowRequest("openai"));

        breaker.onRetryableFailure("openai");

        assertFalse(breaker.allowRequest("openai"));
    }

    @Test
    void permanentFailureDoesNotIncrementTransientOutageCounter() {
        MutableClock clock = new MutableClock();
        TranscriptionCircuitBreaker breaker = new TranscriptionCircuitBreaker(1, Duration.ofSeconds(60), clock);

        breaker.onPermanentFailure("deepgram");

        assertTrue(breaker.allowRequest("deepgram"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-09-23T20:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
