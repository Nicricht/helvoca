package cl.helvoca.operations;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PilotRealBusinessPreflightServiceTest {
    @Mock PilotLaunchControlService launchControl;
    @Mock ControlledPilotExternalEffectGuard externalEffects;
    @Mock TenantProvider tenantProvider;

    private PilotRealBusinessPreflightService service;
    private UUID businessId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        service = new PilotRealBusinessPreflightService(
                launchControl,
                externalEffects,
                tenantProvider);
    }

    @Test
    void readyPilotWithAllEffectsBlockedIsSafeToStart() {
        when(launchControl.current()).thenReturn(launch("READY", "GO", true));
        blockedAll("READY", "GLOBAL_KILL_SWITCH_ACTIVE");

        var result = service.current();

        assertEquals("READY_SAFE", result.state());
        assertTrue(result.readyToStart());
        assertFalse(result.safeToEnableGlobalSwitch());
        assertFalse(result.live());
        assertTrue(result.effects().stream().noneMatch(PilotRealBusinessPreflightService.EffectView::allowed));
    }

    @Test
    void runningPilotWithGlobalSwitchOffIsArmedButStillBlocked() {
        when(launchControl.current()).thenReturn(launch("RUNNING", "RUNNING", false));
        blockedAll("RUNNING", "GLOBAL_KILL_SWITCH_ACTIVE");

        var result = service.current();

        assertEquals("RUNNING_BLOCKED", result.state());
        assertFalse(result.readyToStart());
        assertTrue(result.safeToEnableGlobalSwitch());
        assertFalse(result.live());
    }

    @Test
    void runningPilotWithAllEffectsAllowedIsLive() {
        when(launchControl.current()).thenReturn(launch("RUNNING", "RUNNING", false));
        allowedAll("RUNNING");

        var result = service.current();

        assertEquals("LIVE", result.state());
        assertFalse(result.readyToStart());
        assertFalse(result.safeToEnableGlobalSwitch());
        assertTrue(result.live());
    }

    @Test
    void pausedPilotIsSafeOnlyWhenEveryExternalEffectIsBlocked() {
        when(launchControl.current()).thenReturn(launch("PAUSED", "PAUSED", false));
        blockedAll("PAUSED", "PILOT_NOT_RUNNING");

        var result = service.current();

        assertEquals("PAUSED_SAFE", result.state());
        assertFalse(result.live());
        assertFalse(result.safeToEnableGlobalSwitch());
    }

    @Test
    void nonEnrolledBusinessIsReportedWithoutPretendingPilotSafety() {
        when(launchControl.current()).thenReturn(launch("DRAFT", "NO_GO", false));
        unmanagedAll();

        var result = service.current();

        assertEquals("NOT_ENROLLED", result.state());
        assertFalse(result.readyToStart());
        assertTrue(result.blockers().contains("Pilot control is not enrolled for this business"));
    }

    private PilotLaunchControlService.View launch(String status, String decision, boolean canStart) {
        return new PilotLaunchControlService.View(
                status,
                decision,
                true,
                true,
                List.of(),
                "Pilot Owner",
                "+56911111111",
                "First controlled business",
                Instant.parse("2026-10-04T03:00:00Z"),
                "RUNNING".equals(status) ? Instant.parse("2026-09-27T18:00:00Z") : null,
                null,
                canStart,
                "RUNNING".equals(status),
                "PAUSED".equals(status),
                "RUNNING".equals(status) || "PAUSED".equals(status));
    }

    private void blockedAll(String status, String code) {
        for (ControlledPilotExternalEffectGuard.Effect effect :
                ControlledPilotExternalEffectGuard.Effect.values()) {
            when(externalEffects.evaluate(businessId, effect))
                    .thenReturn(new ControlledPilotExternalEffectGuard.Decision(
                            false, true, code, status, effect));
        }
    }

    private void allowedAll(String status) {
        for (ControlledPilotExternalEffectGuard.Effect effect :
                ControlledPilotExternalEffectGuard.Effect.values()) {
            when(externalEffects.evaluate(businessId, effect))
                    .thenReturn(new ControlledPilotExternalEffectGuard.Decision(
                            true, true, "ALLOWED", status, effect));
        }
    }

    private void unmanagedAll() {
        for (ControlledPilotExternalEffectGuard.Effect effect :
                ControlledPilotExternalEffectGuard.Effect.values()) {
            when(externalEffects.evaluate(businessId, effect))
                    .thenReturn(new ControlledPilotExternalEffectGuard.Decision(
                            true, false, "NOT_PILOT_MANAGED", null, effect));
        }
    }
}
