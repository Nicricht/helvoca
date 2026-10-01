package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformDemoConversionTest {

    @Test
    void finishedDemoCreatesFreshPilotAndCopiesOnlyProfileConfiguration() {
        Fixture f = fixture(DemoSessionState.FINISHED);
        UUID pilotId = UUID.randomUUID();
        when(f.provisioning.provisionPilot(any())).thenReturn(new PlatformBusinessProvisioningResponse(
                pilotId, "Sushi Akira", "America/Santiago", "es",
                "Ana", "ana@example.cl", UUID.randomUUID(), "PENDING",
                Instant.parse("2026-10-04T06:00:00Z"), "/invite.html?token=one-time", "/"));

        PlatformDemoConversionResponse result = f.service.convert(
                f.sessionId, new PlatformDemoConversionRequest("Ana", "ana@example.cl"));

        assertEquals(pilotId, result.pilotBusinessId());
        assertNotEquals(f.runtimeId, result.pilotBusinessId());
        assertEquals("PILOT", result.mode());
        assertFalse(result.idempotentReplay());
        assertEquals(pilotId, f.session.getConvertedPilotBusinessId());
        verify(f.staging).stagePilot(f.profile, pilotId);
        verify(f.provisioning).provisionPilot(argThat(request ->
                request.humanTransferPhone() == null
                        && "Sushi Akira".equals(request.businessName())
                        && "Ana".equals(request.adminName())));
    }

    @Test
    void replayReturnsSamePilotWithoutCreatingSecondTenantOrInvite() {
        Fixture f = fixture(DemoSessionState.FINISHED);
        UUID pilotId = UUID.randomUUID();
        f.session.setConvertedPilotBusinessId(pilotId);
        Business pilot = new Business();
        ReflectionTestUtils.setField(pilot, "id", pilotId);
        pilot.setName("Sushi Akira");
        pilot.setMode(BusinessMode.PILOT);
        when(f.businesses.findById(pilotId)).thenReturn(Optional.of(pilot));

        PlatformDemoConversionResponse replay = f.service.convert(
                f.sessionId, new PlatformDemoConversionRequest("Otra", "otra@example.cl"));

        assertTrue(replay.idempotentReplay());
        assertEquals(pilotId, replay.pilotBusinessId());
        assertNull(replay.invitePath());
        verifyNoInteractions(f.provisioning, f.staging);
    }

    @Test
    void activeFailedOrAbortedDemoCannotConvert() {
        for (DemoSessionState state : new DemoSessionState[]{
                DemoSessionState.ACTIVE, DemoSessionState.FAILED, DemoSessionState.ABORTED}) {
            Fixture f = fixture(state);
            assertThrows(IllegalStateException.class, () ->
                    f.service.convert(f.sessionId,
                            new PlatformDemoConversionRequest("Ana", "ana@example.cl")));
            verifyNoInteractions(f.provisioning, f.staging);
        }
    }

    private static Fixture fixture(DemoSessionState state) {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        PlatformBusinessProvisioningService provisioning = mock(PlatformBusinessProvisioningService.class);
        DemoRuntimeStagingService staging = mock(DemoRuntimeStagingService.class);
        AuditService audit = mock(AuditService.class);

        DemoSession session = DemoSession.preparing(profileId, runtimeId, "rev");
        ReflectionTestUtils.setField(session, "id", sessionId);
        session.markReady();
        if (state == DemoSessionState.ACTIVE) session.markActive();
        else if (state == DemoSessionState.FINISHED) { session.markActive(); session.markFinished(); }
        else if (state == DemoSessionState.FAILED) session.markFailed("failed");
        else if (state == DemoSessionState.ABORTED) session.markAborted();

        DemoProfile profile = new DemoProfile();
        ReflectionTestUtils.setField(profile, "id", profileId);
        profile.setBusinessName("Sushi Akira");
        profile.setTimezone("America/Santiago");
        profile.setLanguage("es");
        profile.setGreeting("Hola");
        profile.setCatalog(Map.of());
        profile.setHours(Map.of());
        profile.setKnowledge(Map.of());

        when(sessions.findForUpdateByIdAndRuntimeBusinessId(sessionId, runtimeId))
                .thenReturn(Optional.of(session));
        when(sessions.saveAndFlush(any(DemoSession.class))).thenAnswer(i -> i.getArgument(0));
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));

        PlatformDemoConversionService service = new PlatformDemoConversionService(
                properties, sessions, profiles, businesses, provisioning, staging, audit);
        return new Fixture(service, runtimeId, sessionId, session, profile,
                businesses, provisioning, staging);
    }

    private record Fixture(
            PlatformDemoConversionService service,
            UUID runtimeId,
            UUID sessionId,
            DemoSession session,
            DemoProfile profile,
            BusinessRepository businesses,
            PlatformBusinessProvisioningService provisioning,
            DemoRuntimeStagingService staging) {}
}
