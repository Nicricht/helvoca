package cl.helvoca.jobs;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistentJobServiceRetryDelayTest {

    @Test
    void requestedMinimumDelayWinsOverShortExponentialBackoff() {
        assertEquals(
                Duration.ofSeconds(60),
                PersistentJobService.retryDelay(1, Duration.ofSeconds(60)));
        assertEquals(
                Duration.ofSeconds(60),
                PersistentJobService.retryDelay(4, Duration.ofSeconds(60)));
    }

    @Test
    void exponentialBackoffStillWinsWhenItBecomesLonger() {
        assertEquals(
                Duration.ofSeconds(80),
                PersistentJobService.retryDelay(5, Duration.ofSeconds(60)));
    }

    @Test
    void noRequestedMinimumKeepsExistingBackoff() {
        assertEquals(
                Duration.ofSeconds(10),
                PersistentJobService.retryDelay(2, Duration.ZERO));
    }
}
