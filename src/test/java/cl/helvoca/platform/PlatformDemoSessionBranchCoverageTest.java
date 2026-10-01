package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformDemoSessionBranchCoverageTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stagingFailureUsesSafeFallbackAndReturnsFailedSession() {
        Fixture f = fixture();
        when(f.sessions.findPreparedForRuntime(f.runtimeId)).thenReturn(Optional.empty());
        when(f.sessions.saveAndFlush(any(DemoSession.class))).thenAnswer(invocation -> {
            DemoSession value = invocation.getArgument(0);
            if (value.getId() == null) ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
            return value;
        });
        doThrow(new RuntimeException()).when(f.staging).stage(f.profile, f.runtimeId);
        when(f.readiness.readiness()).thenReturn(readiness(f.runtimeId, "NOT_CONFIGURED", "SANDBOX_ONLY", "DISARMED"));

        PlatformDemoSessionResponse result = f.service.prepare(f.profileId);

        assertEquals(DemoSessionState.FAILED, result.state());
        assertEquals("Demo preparation failed", result.failureReason());
        verify(f.audit).platformHumanSuccess(
                eq(f.runtimeId), eq("DEMO_SESSION_FAILED"), eq("DEMO_SESSION"),
                any(UUID.class), any(), any());
    }

    @Test
    void currentFallsBackToLatestTerminalThenReturnsNullWhenNoHistoryExists() {
        Fixture f = fixture();
        DemoSession finished = DemoSession.preparing(f.profileId, f.runtimeId, "rev");
        ReflectionTestUtils.setField(finished, "id", UUID.randomUUID());
        finished.markReady();
        finished.markActive();
        finished.markFinished();

        when(f.sessions.findPreparedForRuntime(f.runtimeId)).thenReturn(Optional.empty());
        when(f.sessions.findFirstByRuntimeBusinessIdOrderByCreatedAtDesc(f.runtimeId))
                .thenReturn(Optional.of(finished), Optional.empty());
        when(f.readiness.readiness()).thenReturn(readiness(f.runtimeId, "READY", "SANDBOX_ONLY", "DISARMED"));

        assertEquals(DemoSessionState.FINISHED, f.service.current().state());
        assertNull(f.service.current());
    }

    @Test
    void startRejectsUnavailableMismatchedIncompleteAndUnsafeReadiness() {
        Fixture f = fixture();
        DemoSession ready = readySession(f);
        when(f.sessions.findByIdAndRuntimeBusinessId(f.sessionId, f.runtimeId)).thenReturn(Optional.of(ready));

        when(f.readiness.readiness()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> f.service.start(f.sessionId));

        when(f.readiness.readiness()).thenReturn(new PlatformDemoReadinessResponse(
                false, f.runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));
        assertThrows(IllegalStateException.class, () -> f.service.start(f.sessionId));

        when(f.readiness.readiness()).thenReturn(new PlatformDemoReadinessResponse(
                true, UUID.randomUUID(),
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));
        assertThrows(IllegalStateException.class, () -> f.service.start(f.sessionId));

        when(f.readiness.readiness()).thenReturn(new PlatformDemoReadinessResponse(
                true, f.runtimeId,
                item("READY"), item("READY"), item("NOT_CONFIGURED"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));
        assertThrows(IllegalStateException.class, () -> f.service.start(f.sessionId));

        when(f.readiness.readiness()).thenReturn(new PlatformDemoReadinessResponse(
                true, f.runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), null, item("DISARMED")));
        assertThrows(IllegalStateException.class, () -> f.service.start(f.sessionId));

        when(f.readiness.readiness()).thenReturn(new PlatformDemoReadinessResponse(
                true, f.runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), null));
        assertThrows(IllegalStateException.class, () -> f.service.start(f.sessionId));
    }

    @Test
    void prepareNullOptionalProfileFieldsCreatesNullSafeApprovedSnapshot() {
        Fixture f = fixture();
        f.profile.setCapabilities(null);
        f.profile.setCatalog(null);
        f.profile.setHours(null);
        f.profile.setKnowledge(null);
        f.profile.setInstructions(null);

        when(f.sessions.findPreparedForRuntime(f.runtimeId)).thenReturn(Optional.empty());
        when(f.sessions.saveAndFlush(any(DemoSession.class))).thenAnswer(invocation -> {
            DemoSession value = invocation.getArgument(0);
            if (value.getId() == null) ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
            return value;
        });
        when(f.readiness.readiness()).thenReturn(readiness(f.runtimeId, "READY", "SANDBOX_ONLY", "DISARMED"));

        PlatformDemoSessionResponse result = f.service.prepare(f.profileId);

        assertEquals(DemoSessionState.READY, result.state());
        assertEquals(List.of(), result.configurationSnapshot().get("capabilities"));
        assertEquals(Map.of(), result.configurationSnapshot().get("catalog"));
        assertEquals(Map.of(), result.configurationSnapshot().get("hours"));
        assertEquals(Map.of(), result.configurationSnapshot().get("knowledge"));
        assertNull(result.configurationSnapshot().get("instructions"));
        assertEquals("platform", result.operator());
    }

    @Test
    void getMissingSessionAndMissingRuntimeFailClosed() {
        Fixture f = fixture();
        when(f.sessions.findByIdAndRuntimeBusinessId(f.sessionId, f.runtimeId)).thenReturn(Optional.empty());
        assertThrows(cl.helvoca.common.NotFoundException.class, () -> f.service.get(f.sessionId));

        DemoRuntimeProperties missing = new DemoRuntimeProperties();
        PlatformDemoSessionService noRuntime = new PlatformDemoSessionService(
                missing, f.businesses, f.profiles, f.sessions, f.staging, f.readiness, f.audit);
        assertThrows(IllegalStateException.class, noRuntime::current);
    }

    private static DemoSession readySession(Fixture f) {
        DemoSession session = DemoSession.preparing(f.profileId, f.runtimeId, "rev");
        ReflectionTestUtils.setField(session, "id", f.sessionId);
        session.markReady();
        return session;
    }

    private static Fixture fixture() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
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
        runtime.setName("Demo Runtime");
        runtime.setMode(BusinessMode.DEMO);
        runtime.setStatus(BusinessStatus.ACTIVE);
        when(businesses.findById(runtimeId)).thenReturn(Optional.of(runtime));

        DemoProfile profile = new DemoProfile();
        ReflectionTestUtils.setField(profile, "id", profileId);
        ReflectionTestUtils.setField(profile, "updatedAt", Instant.parse("2026-10-01T05:00:00Z"));
        profile.setBusinessName("Sushi Akira");
        profile.setTimezone("America/Santiago");
        profile.setLanguage("es");
        profile.setGreeting("Hola");
        profile.setCapabilities(List.of("ORDER"));
        profile.setCatalog(Map.of());
        profile.setHours(Map.of());
        profile.setKnowledge(Map.of());
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));

        PlatformDemoSessionService service = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit);
        return new Fixture(service, runtimeId, sessionId, profileId, profile,
                businesses, profiles, sessions, staging, readiness, audit);
    }

    private static PlatformDemoReadinessResponse readiness(
            UUID runtimeId, String dataState, String paymentState, String effectsState) {
        return new PlatformDemoReadinessResponse(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), item(dataState), item("READY"),
                item("NOT_CONFIGURED"), item(paymentState), item(effectsState));
    }

    private static PlatformDemoReadinessResponse.ReadinessItem item(String state) {
        return new PlatformDemoReadinessResponse.ReadinessItem(state, state);
    }

    private record Fixture(
            PlatformDemoSessionService service,
            UUID runtimeId,
            UUID sessionId,
            UUID profileId,
            DemoProfile profile,
            BusinessRepository businesses,
            DemoProfileRepository profiles,
            DemoSessionRepository sessions,
            DemoRuntimeStagingService staging,
            PlatformDemoReadinessService readiness,
            AuditService audit) {}
}
