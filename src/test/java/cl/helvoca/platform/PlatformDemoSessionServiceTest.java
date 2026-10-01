package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformDemoSessionServiceTest {

    @Test
    void prepareFailsClosedWhenRuntimeIsNotConfigured() {
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        DemoRuntimePreparationService staging = mock(DemoRuntimePreparationService.class);
        AuditService audit = mock(AuditService.class);

        PlatformDemoSessionService service =
                new PlatformDemoSessionService(properties, profiles, sessions, businesses, staging, audit);

        assertThrows(IllegalStateException.class,
                () -> service.prepare(UUID.randomUUID(),
                        new PlatformDemoSessionService.PrepareRequest(null, "run-1")));

        verifyNoInteractions(profiles, sessions, businesses, staging, audit);
    }

    @Test
    void prepareRejectsCustomerTenantEvenWhenServerConfigured() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Fixture f = fixture(runtimeId, profileId);
        when(f.businesses.findById(runtimeId))
                .thenReturn(Optional.of(business(runtimeId, BusinessMode.CUSTOMER, BusinessStatus.ACTIVE)));

        assertThrows(ConflictException.class,
                () -> f.service.prepare(profileId,
                        new PlatformDemoSessionService.PrepareRequest(null, "run-1")));

        verifyNoInteractions(f.staging);
        verify(f.sessions, never()).saveAndFlush(any());
    }

    @Test
    void prepareIsIdempotentForSameKeyAndProfile() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Fixture f = fixture(runtimeId, profileId);
        DemoSession existing = session(profileId, runtimeId, "run-1", DemoSessionState.READY);
        when(f.sessions.findByIdempotencyKey("run-1")).thenReturn(Optional.of(existing));

        var result = f.service.prepare(profileId,
                new PlatformDemoSessionService.PrepareRequest("+56911112222", "run-1"));

        assertEquals(existing.getId(), result.id());
        assertEquals(DemoSessionState.READY, result.state());
        verifyNoInteractions(f.businesses, f.staging);
        verify(f.sessions, never()).saveAndFlush(any());
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentProfile() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID otherProfileId = UUID.randomUUID();
        Fixture f = fixture(runtimeId, profileId);
        when(f.sessions.findByIdempotencyKey("run-1"))
                .thenReturn(Optional.of(session(otherProfileId, runtimeId, "run-1", DemoSessionState.READY)));

        assertThrows(ConflictException.class,
                () -> f.service.prepare(profileId,
                        new PlatformDemoSessionService.PrepareRequest(null, "run-1")));
        verifyNoInteractions(f.staging);
    }

    @Test
    void anotherOpenSessionBlocksPrepare() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Fixture f = fixture(runtimeId, profileId);
        when(f.sessions.findFirstOpenSession()).thenReturn(Optional.of(
                session(UUID.randomUUID(), runtimeId, "other-run", DemoSessionState.ACTIVE)));

        assertThrows(ConflictException.class,
                () -> f.service.prepare(profileId,
                        new PlatformDemoSessionService.PrepareRequest(null, "run-1")));
        verifyNoInteractions(f.staging);
    }

    @Test
    void successfulStagingCreatesReadySessionWithoutArmingEffects() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Fixture f = fixture(runtimeId, profileId);
        DemoProfile profile = profile(profileId);
        when(f.profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(f.businesses.findById(runtimeId))
                .thenReturn(Optional.of(business(runtimeId, BusinessMode.DEMO, BusinessStatus.ACTIVE)));
        when(f.staging.stage(runtimeId, profile))
                .thenReturn(new DemoRuntimePreparationService.StageResult("revision-abc"));
        when(f.sessions.saveAndFlush(any())).thenAnswer(invocation -> {
            DemoSession value = invocation.getArgument(0);
            if (value.getId() == null) ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
            return value;
        });

        var result = f.service.prepare(profileId,
                new PlatformDemoSessionService.PrepareRequest("+56911112222", "run-1"));

        assertEquals(DemoSessionState.READY, result.state());
        assertEquals(profileId, result.profileId());
        assertEquals(runtimeId, result.runtimeBusinessId());
        assertEquals("+56911112222", result.expectedParticipantPhone());
        assertEquals("revision-abc", result.configurationRevision());
        assertNull(result.failureReason());
        verify(f.staging).stage(runtimeId, profile);
        verify(f.audit).platformHumanSuccess(eq(runtimeId), eq("DEMO_SESSION_PREPARE"),
                eq("DEMO_SESSION"), any(UUID.class), isNull(), anyMap());
    }

    @Test
    void stagingFailureIsPersistedAsFailedInsteadOfPretendingReady() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Fixture f = fixture(runtimeId, profileId);
        DemoProfile profile = profile(profileId);
        when(f.profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(f.businesses.findById(runtimeId))
                .thenReturn(Optional.of(business(runtimeId, BusinessMode.DEMO, BusinessStatus.ACTIVE)));
        when(f.staging.stage(runtimeId, profile)).thenThrow(new IllegalStateException("catalog staging failed"));
        when(f.sessions.saveAndFlush(any())).thenAnswer(invocation -> {
            DemoSession value = invocation.getArgument(0);
            if (value.getId() == null) ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
            return value;
        });

        var result = f.service.prepare(profileId,
                new PlatformDemoSessionService.PrepareRequest(null, "run-1"));

        assertEquals(DemoSessionState.FAILED, result.state());
        assertTrue(result.failureReason().contains("catalog staging failed"));
        assertNull(result.configurationRevision());
        verify(f.sessions, atLeast(2)).saveAndFlush(any());
    }

    @Test
    void unknownProfileIsNotFoundBeforeAnyRuntimeMutation() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Fixture f = fixture(runtimeId, profileId);
        when(f.businesses.findById(runtimeId))
                .thenReturn(Optional.of(business(runtimeId, BusinessMode.DEMO, BusinessStatus.ACTIVE)));
        when(f.profiles.findById(profileId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> f.service.prepare(profileId,
                        new PlatformDemoSessionService.PrepareRequest(null, "run-1")));

        verifyNoInteractions(f.staging);
    }

    private static Fixture fixture(UUID runtimeId, UUID profileId) {
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        DemoRuntimePreparationService staging = mock(DemoRuntimePreparationService.class);
        AuditService audit = mock(AuditService.class);
        when(sessions.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(sessions.findFirstOpenSession()).thenReturn(Optional.empty());
        return new Fixture(
                new PlatformDemoSessionService(properties, profiles, sessions, businesses, staging, audit),
                profiles, sessions, businesses, staging, audit);
    }

    private static DemoProfile profile(UUID id) {
        DemoProfile profile = new DemoProfile();
        ReflectionTestUtils.setField(profile, "id", id);
        profile.setDisplayName("Sushi Akira");
        profile.setBusinessName("Sushi Akira");
        profile.setTimezone("America/Santiago");
        profile.setLanguage("es");
        profile.setGreeting("Hola");
        ReflectionTestUtils.setField(profile, "updatedAt", Instant.parse("2026-10-01T05:00:00Z"));
        return profile;
    }

    private static DemoSession session(UUID profileId,
                                       UUID runtimeId,
                                       String key,
                                       DemoSessionState state) {
        DemoSession session = new DemoSession();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        session.setProfileId(profileId);
        session.setRuntimeBusinessId(runtimeId);
        session.setIdempotencyKey(key);
        session.setState(state);
        return session;
    }

    private static Business business(UUID id, BusinessMode mode, BusinessStatus status) {
        Business business = new Business();
        ReflectionTestUtils.setField(business, "id", id);
        business.setName("Live Demo Runtime");
        business.setMode(mode);
        business.setStatus(status);
        return business;
    }

    private record Fixture(
            PlatformDemoSessionService service,
            DemoProfileRepository profiles,
            DemoSessionRepository sessions,
            BusinessRepository businesses,
            DemoRuntimePreparationService staging,
            AuditService audit) {}
}
