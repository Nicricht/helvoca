package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformDemoSessionLifecycleExtensionTest {

    @Test
    void readyStartsActiveThenFinishesAndCannotRestart() {
        Fixture f = fixture(DemoSessionState.READY);
        when(f.readiness.readiness()).thenReturn(ready(f.runtimeId));

        PlatformDemoSessionResponse active = f.service.start(f.sessionId);
        assertEquals(DemoSessionState.ACTIVE, active.state());
        assertNotNull(active.startedAt());
        assertEquals("DISARMED", active.externalEffectsState());
        assertEquals("SANDBOX_ONLY", active.paymentState());

        PlatformDemoSessionResponse finished = f.service.finish(f.sessionId);
        assertEquals(DemoSessionState.FINISHED, finished.state());
        assertNotNull(finished.finishedAt());
        assertThrows(IllegalStateException.class, () -> f.service.start(f.sessionId));
    }

    @Test
    void readyOrActiveCanAbortButFinishedCannot() {
        Fixture ready = fixture(DemoSessionState.READY);
        when(ready.readiness.readiness()).thenReturn(ready(ready.runtimeId));
        assertEquals(DemoSessionState.ABORTED, ready.service.abort(ready.sessionId).state());

        Fixture active = fixture(DemoSessionState.ACTIVE);
        when(active.readiness.readiness()).thenReturn(ready(active.runtimeId));
        assertEquals(DemoSessionState.ABORTED, active.service.abort(active.sessionId).state());

        Fixture finished = fixture(DemoSessionState.FINISHED);
        assertThrows(IllegalStateException.class, () -> finished.service.abort(finished.sessionId));
    }

    @Test
    void startFailsClosedIfPaymentOrExternalEffectsBoundaryChanges() {
        Fixture f = fixture(DemoSessionState.READY);
        PlatformDemoReadinessResponse unsafePayment = new PlatformDemoReadinessResponse(
                true, f.runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("READY"), item("DISARMED"));
        when(f.readiness.readiness()).thenReturn(unsafePayment);
        assertThrows(IllegalStateException.class, () -> f.service.start(f.sessionId));

        PlatformDemoReadinessResponse unsafeEffects = new PlatformDemoReadinessResponse(
                true, f.runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("ARMED"));
        when(f.readiness.readiness()).thenReturn(unsafeEffects);
        assertThrows(IllegalStateException.class, () -> f.service.start(f.sessionId));
    }

    private static Fixture fixture(DemoSessionState state) {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        BusinessRepository businesses = mock(BusinessRepository.class);
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoRuntimeStagingService staging = mock(DemoRuntimeStagingService.class);
        PlatformDemoReadinessService readiness = mock(PlatformDemoReadinessService.class);
        AuditService audit = mock(AuditService.class);

        Business runtime = new Business();
        ReflectionTestUtils.setField(runtime, "id", runtimeId);
        runtime.setMode(BusinessMode.DEMO);
        runtime.setStatus(BusinessStatus.ACTIVE);
        when(businesses.findById(runtimeId)).thenReturn(Optional.of(runtime));

        DemoSession session = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(session, "id", sessionId);
        if (state == DemoSessionState.READY) session.markReady();
        else if (state == DemoSessionState.ACTIVE) { session.markReady(); session.markActive(); }
        else if (state == DemoSessionState.FINISHED) { session.markReady(); session.markActive(); session.markFinished(); }
        else if (state == DemoSessionState.ABORTED) { session.markReady(); session.markAborted(); }
        when(sessions.findByIdAndRuntimeBusinessId(sessionId, runtimeId)).thenReturn(Optional.of(session));
        when(sessions.saveAndFlush(any(DemoSession.class))).thenAnswer(i -> i.getArgument(0));

        PlatformDemoSessionService service = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit);
        return new Fixture(service, runtimeId, sessionId, readiness);
    }

    private static PlatformDemoReadinessResponse ready(UUID runtimeId) {
        return new PlatformDemoReadinessResponse(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED"));
    }

    private static PlatformDemoReadinessResponse.ReadinessItem item(String state) {
        return new PlatformDemoReadinessResponse.ReadinessItem(state, state);
    }

    private record Fixture(
            PlatformDemoSessionService service,
            UUID runtimeId,
            UUID sessionId,
            PlatformDemoReadinessService readiness) {}
}
