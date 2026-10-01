package cl.helvoca.telephony.twilio;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioCertificationCommandBootstrapRunnerTest {

    @Test
    void emptyBootstrapDoesNothing() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationCommandBootstrapRunner runner =
                new TwilioCertificationCommandBootstrapRunner("", store, new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        verifyNoInteractions(store);
    }

    @Test
    void validBootstrapEnqueuesOnlyRunIdWithFixedActor() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationCommandBootstrapRunner runner =
                new TwilioCertificationCommandBootstrapRunner(" latency-bootstrap-20260927-001 ", store, new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        verify(store).enqueue("latency-bootstrap-20260927-001", "startup-bootstrap");
    }

    @Test
    void invalidBootstrapFailsClosed() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationCommandBootstrapRunner runner =
                new TwilioCertificationCommandBootstrapRunner("bad token", store, new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        verifyNoInteractions(store);
    }
    @Test
    void validBootstrapUsesExplicitSystemScopeAndRestoresDenied() {
        TenantDatabaseContext databaseContext = new TenantDatabaseContext();
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        when(store.enqueue("latency-bootstrap-20261001-001", "startup-bootstrap"))
                .thenAnswer(invocation -> {
                    assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
                    return true;
                });
        TwilioCertificationCommandBootstrapRunner runner =
                new TwilioCertificationCommandBootstrapRunner(
                        "latency-bootstrap-20261001-001", store, databaseContext);

        runner.run(mock(ApplicationArguments.class));

        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
    }

}
