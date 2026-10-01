package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
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
    void existingPreparingSessionIsIdempotentAndDoesNotStageTwice() {
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
        DemoSession existing = DemoSession.preparing(profileId, runtimeId, profile.getUpdatedAt().toString());
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

        when(businesses.findById(runtimeId)).thenReturn(Optional.of(runtime));
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.of(existing));
        when(readiness.readiness()).thenReturn(readiness(runtimeId, "READY", "READY", "NOT_CONFIGURED"));

        PlatformDemoSessionResponse result = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit).prepare(profileId);

        assertEquals(existing.getId(), result.id());
        assertEquals(DemoSessionState.PREPARING, result.state());
        verifyNoInteractions(staging);
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void incompleteRequiredReadinessFailsClosedAfterControlledStaging() {
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

        when(businesses.findById(runtimeId))
                .thenReturn(Optional.of(runtime(runtimeId, BusinessMode.DEMO, BusinessStatus.ACTIVE)));
        when(profiles.findById(profileId))
                .thenReturn(Optional.of(profile(profileId, Instant.parse("2026-10-01T05:00:00Z"))));
        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.empty());
        when(sessions.saveAndFlush(any(DemoSession.class))).thenAnswer(invocation -> {
            DemoSession session = invocation.getArgument(0);
            if (session.getId() == null) ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
            return session;
        });
        PlatformDemoReadinessResponse unavailable = new PlatformDemoReadinessResponse(
                true, runtimeId,
                item("READY", "runtime"),
                item("READY", "number"),
                item("UNAVAILABLE", "voice"),
                item("READY", "data"),
                item("READY", "ops"),
                item("NOT_CONFIGURED", "wa"),
                item("SANDBOX_ONLY", "payment"),
                item("DISARMED", "effects"));
        when(readiness.readiness()).thenReturn(unavailable);

        PlatformDemoSessionResponse result = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit).prepare(profileId);

        assertEquals(DemoSessionState.FAILED, result.state());
        assertTrue(result.failureReason().contains("voiceAi"));
        verify(staging).stage(any(DemoProfile.class), eq(runtimeId));
        verify(audit).platformHumanSuccess(
                eq(runtimeId), eq("DEMO_SESSION_FAILED"), eq("DEMO_SESSION"),
                any(UUID.class), any(), any());
    }

    @Test
    void stalePreparingSessionResumesControlledStagingInsteadOfRemainingWedged() {
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

        DemoProfile profile = profile(profileId, Instant.parse("2026-10-01T05:00:00Z"));
        DemoSession stale = DemoSession.preparing(profileId, runtimeId, profile.getUpdatedAt().toString());
        ReflectionTestUtils.setField(stale, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(stale, "updatedAt", Instant.now().minusSeconds(600));

        when(businesses.findById(runtimeId))
                .thenReturn(Optional.of(runtime(runtimeId, BusinessMode.DEMO, BusinessStatus.ACTIVE)));
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.of(stale));
        when(sessions.saveAndFlush(any(DemoSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(readiness.readiness()).thenReturn(readiness(runtimeId, "READY", "READY", "READY"));

        PlatformDemoSessionResponse result = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit).prepare(profileId);

        assertEquals(DemoSessionState.READY, result.state());
        verify(staging).stage(profile, runtimeId);
        verify(audit).platformHumanSuccess(
                eq(runtimeId), eq("DEMO_SESSION_PREPARE_RESUME"), eq("DEMO_SESSION"),
                eq(stale.getId()), any(), any());
        verify(audit).platformHumanSuccess(
                eq(runtimeId), eq("DEMO_SESSION_READY"), eq("DEMO_SESSION"),
                eq(stale.getId()), any(), any());
    }

    @Test
    void concurrentPrepareReturnsWinningSameRevisionSessionWithoutStagingTwice() {
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

        DemoProfile profile = profile(profileId, Instant.parse("2026-10-01T05:00:00Z"));
        DemoSession winner = DemoSession.preparing(profileId, runtimeId, profile.getUpdatedAt().toString());
        winner.markReady();
        ReflectionTestUtils.setField(winner, "id", UUID.randomUUID());

        when(businesses.findById(runtimeId))
                .thenReturn(Optional.of(runtime(runtimeId, BusinessMode.DEMO, BusinessStatus.ACTIVE)));
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(sessions.findPreparedForRuntime(runtimeId))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(sessions.saveAndFlush(any(DemoSession.class)))
                .thenThrow(new DataIntegrityViolationException("unique live runtime"));
        when(readiness.readiness()).thenReturn(readiness(runtimeId, "READY", "READY", "READY"));

        PlatformDemoSessionResponse result = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit).prepare(profileId);

        assertEquals(winner.getId(), result.id());
        assertEquals(DemoSessionState.READY, result.state());
        verifyNoInteractions(staging);
    }

    @Test
    void concurrentPrepareWithDifferentProfileFailsAsConflict() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID otherProfileId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        BusinessRepository businesses = mock(BusinessRepository.class);
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoRuntimeStagingService staging = mock(DemoRuntimeStagingService.class);
        PlatformDemoReadinessService readiness = mock(PlatformDemoReadinessService.class);
        AuditService audit = mock(AuditService.class);

        DemoProfile profile = profile(profileId, Instant.parse("2026-10-01T05:00:00Z"));
        DemoSession winner = DemoSession.preparing(otherProfileId, runtimeId, "other-revision");
        ReflectionTestUtils.setField(winner, "id", UUID.randomUUID());

        when(businesses.findById(runtimeId))
                .thenReturn(Optional.of(runtime(runtimeId, BusinessMode.DEMO, BusinessStatus.ACTIVE)));
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(sessions.findPreparedForRuntime(runtimeId))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(sessions.saveAndFlush(any(DemoSession.class)))
                .thenThrow(new DataIntegrityViolationException("unique live runtime"));

        PlatformDemoSessionService service = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit);

        assertThrows(ConflictException.class, () -> service.prepare(profileId));
        verifyNoInteractions(staging);
    }

    @Test
    void preparedDifferentRevisionIsRejectedBeforeAnyRuntimeMutation() {
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

        DemoProfile profile = profile(profileId, Instant.parse("2026-10-01T05:00:00Z"));
        DemoSession existing = DemoSession.preparing(profileId, runtimeId, "older-revision");
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

        when(businesses.findById(runtimeId))
                .thenReturn(Optional.of(runtime(runtimeId, BusinessMode.DEMO, BusinessStatus.ACTIVE)));
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.of(existing));

        PlatformDemoSessionService service = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit);

        assertThrows(ConflictException.class, () -> service.prepare(profileId));
        verifyNoInteractions(staging, readiness, audit);
    }

    @Test
    void currentAndGetNeverExposeSessionsFromAnotherConfiguredRuntime() {
        UUID runtimeId = UUID.randomUUID();
        UUID foreignRuntime = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        BusinessRepository businesses = mock(BusinessRepository.class);
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoRuntimeStagingService staging = mock(DemoRuntimeStagingService.class);
        PlatformDemoReadinessService readiness = mock(PlatformDemoReadinessService.class);
        AuditService audit = mock(AuditService.class);

        DemoSession foreign = DemoSession.preparing(UUID.randomUUID(), foreignRuntime, "rev");
        ReflectionTestUtils.setField(foreign, "id", sessionId);
        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.empty());
        when(sessions.findById(sessionId)).thenReturn(Optional.of(foreign));

        PlatformDemoSessionService service = new PlatformDemoSessionService(
                properties, businesses, profiles, sessions, staging, readiness, audit);

        assertNull(service.current());
        assertThrows(NotFoundException.class, () -> service.get(sessionId));
        verifyNoInteractions(readiness);
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
