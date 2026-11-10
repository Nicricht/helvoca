package cl.helvoca.telephony.twilio;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioCertificationCommandWorkerTest {

    @Test
    void pinnedModeClaimsOnlyExactBootstrapRunAndNeverFallsBackToGenericQueue() throws Exception {
        var store = mock(TwilioCertificationCommandStore.class);
        var launcher = mock(TwilioCertificationCommandCallLauncher.class);
        var binding = mock(TwilioCertificationDemoBindingGate.class);
        UUID business = UUID.randomUUID();
        String run = "aurora-one-20261109-001";
        var command = new TwilioCertificationCommandStore.ClaimedCommand(run,
                "11111111-1111-1111-1111-111111111111", business);
        when(binding.pinnedBusinessId()).thenReturn(business);
        when(binding.allows(business)).thenReturn(true);
        when(store.claimExactRunId(run, business, "startup-bootstrap"))
                .thenReturn(Optional.of(command), Optional.empty());
        when(launcher.launch(command.callbackToken())).thenReturn("CA" + "a".repeat(32));
        var worker = new TwilioCertificationCommandWorker(true, run, "demo-isolated", store,
                launcher, new TenantDatabaseContext(), binding);

        worker.poll();
        worker.poll();

        verify(store, times(2)).claimExactRunId(run, business, "startup-bootstrap");
        verify(store, never()).claimNext();
        verify(launcher, times(1)).launch(command.callbackToken());
    }

    @Test
    void invalidPinEnvironmentOrBusinessFailsClosed() {
        var store = mock(TwilioCertificationCommandStore.class);
        var launcher = mock(TwilioCertificationCommandCallLauncher.class);
        var binding = mock(TwilioCertificationDemoBindingGate.class);
        for (String run : new String[]{"bad", "aurora-one-20261109-001"}) {
            var worker = new TwilioCertificationCommandWorker(true, run, "production", store,
                    launcher, new TenantDatabaseContext(), binding);
            worker.poll();
        }
        new TwilioCertificationCommandWorker(true, "bad", "demo-isolated", store,
                launcher, new TenantDatabaseContext(), binding).poll();
        new TwilioCertificationCommandWorker(true, " ", "demo-isolated", store,
                launcher, new TenantDatabaseContext(), binding).poll();
        new TwilioCertificationCommandWorker(true, "aurora-one-20261109-001", "demo-isolated", store,
                launcher, new TenantDatabaseContext(), binding).poll();
        verifyNoInteractions(store, launcher);
    }

    @Test
    void ambiguousCarrierFailureNeverRetriesPinnedCommand() throws Exception {
        var store = mock(TwilioCertificationCommandStore.class);
        var launcher = mock(TwilioCertificationCommandCallLauncher.class);
        var binding = mock(TwilioCertificationDemoBindingGate.class);
        UUID business = UUID.randomUUID();
        String run = "aurora-one-20261109-002";
        var command = new TwilioCertificationCommandStore.ClaimedCommand(run,
                "22222222-2222-2222-2222-222222222222", business);
        when(binding.pinnedBusinessId()).thenReturn(business);
        when(binding.allows(business)).thenReturn(true);
        when(store.claimExactRunId(run, business, "startup-bootstrap")).thenReturn(Optional.of(command), Optional.empty());
        when(launcher.launch(command.callbackToken())).thenThrow(new IllegalStateException("timeout"));
        var worker = new TwilioCertificationCommandWorker(true, run, "demo-isolated", store,
                launcher, new TenantDatabaseContext(), binding);
        worker.poll();
        worker.poll();
        verify(launcher, times(1)).launch(command.callbackToken());
        verify(store).markFailed(run, "timeout");
        verify(store, never()).claimNext();
    }

    @Test
    void mismatchedDemoBindingFailsBeforeCarrierCall() {
        var store = mock(TwilioCertificationCommandStore.class);
        var launcher = mock(TwilioCertificationCommandCallLauncher.class);
        var binding = mock(TwilioCertificationDemoBindingGate.class);
        var command = new TwilioCertificationCommandStore.ClaimedCommand(
                "aurora-demo-20261019-001", "11111111-1111-1111-1111-111111111111", UUID.randomUUID());
        when(store.claimNext()).thenReturn(Optional.of(command));
        var worker = new TwilioCertificationCommandWorker(true, store, launcher,
                new TenantDatabaseContext(), binding);

        worker.poll();

        verify(binding).allows(command.businessId());
        verifyNoInteractions(launcher);
        verify(store).markFailed(command.runId(), "certification DEMO tenant/phone binding unavailable");
    }

    @Test
    void certifiedDemoCommandLaunchesWithoutAnApplicationDurationLimit() throws Exception {
        var store = mock(TwilioCertificationCommandStore.class);
        var launcher = mock(TwilioCertificationCommandCallLauncher.class);
        var binding = mock(TwilioCertificationDemoBindingGate.class);
        var command = new TwilioCertificationCommandStore.ClaimedCommand(
                "aurora-demo-20261019-002", "22222222-2222-2222-2222-222222222222", UUID.randomUUID());
        when(store.claimNext()).thenReturn(Optional.of(command));
        when(binding.allows(command.businessId())).thenReturn(true);
        when(launcher.launch(command.callbackToken())).thenReturn("CA" + "b".repeat(32));
        var worker = new TwilioCertificationCommandWorker(true, store, launcher,
                new TenantDatabaseContext(), binding);

        worker.poll();

        verify(binding).allows(command.businessId());
        verify(launcher).launch(command.callbackToken());
        verify(store).recordProviderCall(command.runId(), command.callbackToken(), "CA" + "b".repeat(32));
        verify(store, never()).markFailed(anyString(), anyString());
    }

    @Test
    void disabledWorkerDoesNotTouchQueue() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationCommandCallLauncher launcher = mock(TwilioCertificationCommandCallLauncher.class);
        TwilioCertificationCommandWorker worker =
                new TwilioCertificationCommandWorker(false, store, launcher, new TenantDatabaseContext());

        worker.poll();

        verifyNoInteractions(store, launcher);
    }

    @Test
    void emptyQueueDoesNothing() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationCommandCallLauncher launcher = mock(TwilioCertificationCommandCallLauncher.class);
        when(store.claimNext()).thenReturn(Optional.empty());
        TwilioCertificationCommandWorker worker =
                new TwilioCertificationCommandWorker(true, store, launcher, new TenantDatabaseContext());

        worker.poll();

        verify(store).claimNext();
        verifyNoInteractions(launcher);
    }

    @Test
    void claimedCommandLaunchesExactlyOnceAndRecordsProviderCall() throws Exception {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationCommandCallLauncher launcher = mock(TwilioCertificationCommandCallLauncher.class);
        var command = new TwilioCertificationCommandStore.ClaimedCommand(
                "latency-20260927-001", "11111111-1111-1111-1111-111111111111");
        when(store.claimNext()).thenReturn(Optional.of(command));
        when(launcher.launch(command.callbackToken()))
                .thenReturn("CA0123456789abcdef0123456789abcdef");
        TwilioCertificationCommandWorker worker =
                new TwilioCertificationCommandWorker(true, store, launcher, new TenantDatabaseContext());

        worker.poll();

        verify(launcher, times(1)).launch(command.callbackToken());
        verify(store).recordProviderCall(
                command.runId(), command.callbackToken(), "CA0123456789abcdef0123456789abcdef");
        verify(store, never()).markFailed(anyString(), anyString());
    }

    @Test
    void launchFailureMarksCommandFailed() throws Exception {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationCommandCallLauncher launcher = mock(TwilioCertificationCommandCallLauncher.class);
        var command = new TwilioCertificationCommandStore.ClaimedCommand(
                "latency-20260927-002", "22222222-2222-2222-2222-222222222222");
        when(store.claimNext()).thenReturn(Optional.of(command));
        when(launcher.launch(command.callbackToken()))
                .thenThrow(new IllegalStateException("provider unavailable"));
        TwilioCertificationCommandWorker worker =
                new TwilioCertificationCommandWorker(true, store, launcher, new TenantDatabaseContext());

        worker.poll();

        verify(store).markFailed(command.runId(), "provider unavailable");
        verify(store, never()).recordProviderCall(anyString(), anyString(), anyString());
    }
    @Test
    void queueDatabaseAccessUsesExplicitSystemScopeAndRestoresDenied() throws Exception {
        TenantDatabaseContext databaseContext = new TenantDatabaseContext();
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationCommandCallLauncher launcher = mock(TwilioCertificationCommandCallLauncher.class);
        var command = new TwilioCertificationCommandStore.ClaimedCommand(
                "latency-20261001-001", "33333333-3333-3333-3333-333333333333");

        when(store.claimNext()).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
            return Optional.of(command);
        });
        when(launcher.launch(command.callbackToken())).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
            return "CA0123456789abcdef0123456789abcdef";
        });
        doAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
            return null;
        }).when(store).recordProviderCall(
                command.runId(), command.callbackToken(), "CA0123456789abcdef0123456789abcdef");

        TwilioCertificationCommandWorker worker =
                new TwilioCertificationCommandWorker(true, store, launcher, databaseContext);

        worker.poll();

        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
    }

}
