package cl.helvoca.telephony.twilio;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioCertificationCommandWorkerTest {

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
    void durationOverSeventyFiveSecondsFailsBeforeCarrierCall() throws Exception {
        var store = mock(TwilioCertificationCommandStore.class);
        var launcher = mock(TwilioCertificationCommandCallLauncher.class);
        var binding = mock(TwilioCertificationDemoBindingGate.class);
        var command = new TwilioCertificationCommandStore.ClaimedCommand(
                "aurora-demo-20261019-002", "22222222-2222-2222-2222-222222222222", UUID.randomUUID());
        when(store.claimNext()).thenReturn(Optional.of(command));
        when(binding.allows(command.businessId())).thenReturn(true);
        var worker = new TwilioCertificationCommandWorker(true, store, launcher,
                new TenantDatabaseContext(), binding);

        worker.poll();

        verify(launcher).durationWithinDemoLimit();
        verify(launcher, never()).launch(anyString());
        verify(store).markFailed(command.runId(), "certification DEMO duration exceeds 75 seconds");
    }

    @Test
    void certifiedDemoAndBoundedDurationLaunchesOnce() throws Exception {
        var store = mock(TwilioCertificationCommandStore.class);
        var launcher = mock(TwilioCertificationCommandCallLauncher.class);
        var binding = mock(TwilioCertificationDemoBindingGate.class);
        var command = new TwilioCertificationCommandStore.ClaimedCommand(
                "aurora-demo-20261019-003", "33333333-3333-3333-3333-333333333333", UUID.randomUUID());
        when(store.claimNext()).thenReturn(Optional.of(command));
        when(binding.allows(command.businessId())).thenReturn(true);
        when(launcher.durationWithinDemoLimit()).thenReturn(true);
        when(launcher.launch(command.callbackToken())).thenReturn("CA" + "a".repeat(32));
        var worker = new TwilioCertificationCommandWorker(true, store, launcher,
                new TenantDatabaseContext(), binding);

        worker.poll();

        verify(launcher).launch(command.callbackToken());
        verify(store).recordProviderCall(command.runId(), command.callbackToken(), "CA" + "a".repeat(32));
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
