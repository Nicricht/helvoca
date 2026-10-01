package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformDemoSessionServiceTest {

    @Test
    void prepareUsesOnlyServerOwnedDemoRuntimeAndStagesApprovedProfile() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        BusinessRepository businesses = mock(BusinessRepository.class);
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoRuntimeStagingService staging = mock(DemoRuntimeStagingService.class);
        PlatformDemoReadinessService readiness = mock(PlatformDemoReadinessService.class);
        AuditService audit = mock(AuditService.class);

        Business runtime = runtime(runtimeId, BusinessMode.DEMO, BusinessStatus.ACTIVE);
        DemoProfile profile = profile(profileId, Instant.parse("2026-10-01T05:00:00Z"));
        when(businesses.findById(runtimeId)).thenReturn(Optional.of(runtime));
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.empty());
        when(sessions.saveAndFlush(any(DemoSession.class))).thenAnswer(invocation -> {
            DemoSession session = invocation.getArgument(0);
            if (session.getId() == null) ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
            return session;
        });
        PlatformDemoReadinessResponse ready = readiness(runtimeId, "READY", "READY", "READY");
        when(readiness.readiness()).thenReturn(ready);

        PlatformDemoSessionService service = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit);

        PlatformDemoSessionResponse result = service.prepare(profileId);

        assertEquals(runtimeId, result.runtimeBusinessId());
        assertEquals(profileId, result.demoProfileId());
        assertEquals(DemoSessionState.READY, result.state());
        assertNotNull(result.configurationRevision());
        verify(staging).stage(profile, runtimeId);
        verify(audit, atLeastOnce()).platformHumanSuccess(
                eq(runtimeId), anyString(), eq("DEMO_SESSION"), any(UUID.class), any(), any());
    }

    @Test
    void duplicatePrepareForSameProfileRevisionIsIdempotent() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        BusinessRepository businesses = mock(BusinessRepository.class);
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoRuntimeStagingService staging = mock(DemoRuntimeStagingService.class);
        PlatformDemoReadinessService readiness = mock(PlatformDemoReadinessService.class);
        AuditService audit = mock(AuditService.class);

        Business runtime = runtime(runtimeId, BusinessMode.DEMO, BusinessStatus.ACTIVE);
        DemoProfile profile = profile(profileId, Instant.parse("2026-10-01T05:00:00Z"));
        DemoSession existing = DemoSession.preparing(profileId, runtimeId, "2026-10-01T05:00:00Z");
        existing.markStaged();
        existing.markReady();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

        when(businesses.findById(runtimeId)).thenReturn(Optional.of(runtime));
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.of(existing));
        when(readiness.readiness()).thenReturn(readiness(runtimeId, "READY", "READY", "READY"));

        PlatformDemoSessionResponse result = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit).prepare(profileId);

        assertEquals(existing.getId(), result.id());
        assertEquals(DemoSessionState.READY, result.state());
        verifyNoInteractions(staging);
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void customerOrSuspendedRuntimeFailsClosedBeforeStaging() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        BusinessRepository businesses = mock(BusinessRepository.class);
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoRuntimeStagingService staging = mock(DemoRuntimeStagingService.class);
        PlatformDemoReadinessService readiness = mock(PlatformDemoReadinessService.class);
        AuditService audit = mock(AuditService.class);

        when(businesses.findById(runtimeId)).thenReturn(Optional.of(
                runtime(runtimeId, BusinessMode.CUSTOMER, BusinessStatus.ACTIVE)));

        PlatformDemoSessionService service = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit);

        assertThrows(IllegalStateException.class, () -> service.prepare(profileId));
        verifyNoInteractions(profiles, sessions, staging, readiness, audit);
    }

    private static PlatformDemoReadinessResponse readiness(
            UUID runtimeId, String runtime, String voice, String data) {
        return new PlatformDemoReadinessResponse(
                true, runtimeId,
                item(runtime, "runtime"),
                item(voice, "number"),
                item(voice, "ai"),
                item(data, "data"),
                item("READY", "ops"),
                item("NOT_CONFIGURED", "wa"),
                item("SANDBOX_ONLY", "payment"),
                item("DISARMED", "effects"));
    }

    private static PlatformDemoReadinessResponse.ReadinessItem item(String state, String detail) {
        return new PlatformDemoReadinessResponse.ReadinessItem(state, detail);
    }

    private static Business runtime(UUID id, BusinessMode mode, BusinessStatus status) {
        Business business = new Business();
        ReflectionTestUtils.setField(business, "id", id);
        business.setName("Live Demo Runtime");
        business.setMode(mode);
        business.setStatus(status);
        return business;
    }

    private static DemoProfile profile(UUID id, Instant updatedAt) {
        DemoProfile profile = new DemoProfile();
        ReflectionTestUtils.setField(profile, "id", id);
        ReflectionTestUtils.setField(profile, "updatedAt", updatedAt);
        profile.setDisplayName("Sushi Akira");
        profile.setBusinessName("Sushi Akira");
        profile.setTimezone("America/Santiago");
        profile.setLanguage("es");
        profile.setGreeting("Hola");
        return profile;
    }
}
