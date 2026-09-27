package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.*;

class TwilioCertificationCommandBootstrapRunnerTest {

    @Test
    void emptyBootstrapDoesNothing() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationCommandBootstrapRunner runner =
                new TwilioCertificationCommandBootstrapRunner("", store);

        runner.run(mock(ApplicationArguments.class));

        verifyNoInteractions(store);
    }

    @Test
    void validBootstrapEnqueuesOnlyRunIdWithFixedActor() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationCommandBootstrapRunner runner =
                new TwilioCertificationCommandBootstrapRunner(" latency-bootstrap-20260927-001 ", store);

        runner.run(mock(ApplicationArguments.class));

        verify(store).enqueue("latency-bootstrap-20260927-001", "startup-bootstrap");
    }

    @Test
    void invalidBootstrapFailsClosed() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationCommandBootstrapRunner runner =
                new TwilioCertificationCommandBootstrapRunner("bad token", store);

        runner.run(mock(ApplicationArguments.class));

        verifyNoInteractions(store);
    }
}
