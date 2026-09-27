package cl.helvoca.operations;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControlledPilotExternalEffectGuardTest {

    @Test
    void nonPilotTenantPreservesExistingBehaviorEvenWhenGlobalPilotSwitchIsOff() {
        UUID businessId = UUID.randomUUID();
        PilotLaunchControlRepository controls = mock(PilotLaunchControlRepository.class);
        when(controls.findById(businessId)).thenReturn(Optional.empty());

        ControlledPilotExternalEffectGuard guard =
                new ControlledPilotExternalEffectGuard(controls, false);

        var decision = guard.evaluate(
                businessId,
                ControlledPilotExternalEffectGuard.Effect.WHATSAPP);

        assertTrue(decision.allowed());
        assertFalse(decision.pilotManaged());
        assertEquals("NOT_PILOT_MANAGED", decision.code());
    }

    @Test
    void exposesGlobalSwitchWithoutChangingPilotState() {
        PilotLaunchControlRepository controls = mock(PilotLaunchControlRepository.class);

        assertFalse(new ControlledPilotExternalEffectGuard(controls, false)
                .globalExternalEffectsEnabled());
        assertTrue(new ControlledPilotExternalEffectGuard(controls, true)
                .globalExternalEffectsEnabled());

        verifyNoInteractions(controls);
    }

    @Test
    void runningPilotAllowsExternalEffectsOnlyAfterGlobalOptIn() {
        UUID businessId = UUID.randomUUID();
        PilotLaunchControlRepository controls = mock(PilotLaunchControlRepository.class);
        when(controls.findById(businessId))
                .thenReturn(Optional.of(control(businessId, PilotLaunchControl.Status.RUNNING)));

        ControlledPilotExternalEffectGuard guard =
                new ControlledPilotExternalEffectGuard(controls, true);

        var decision = guard.evaluate(
                businessId,
                ControlledPilotExternalEffectGuard.Effect.PAYMENT);

        assertTrue(decision.allowed());
        assertTrue(decision.pilotManaged());
        assertEquals("ALLOWED", decision.code());
        assertEquals("RUNNING", decision.status());
    }

    @Test
    void pausedPilotIsARealPerBusinessKillSwitch() {
        UUID businessId = UUID.randomUUID();
        PilotLaunchControlRepository controls = mock(PilotLaunchControlRepository.class);
        when(controls.findById(businessId))
                .thenReturn(Optional.of(control(businessId, PilotLaunchControl.Status.PAUSED)));

        ControlledPilotExternalEffectGuard guard =
                new ControlledPilotExternalEffectGuard(controls, true);

        var decision = guard.evaluate(
                businessId,
                ControlledPilotExternalEffectGuard.Effect.VOICE);

        assertFalse(decision.allowed());
        assertEquals("PILOT_NOT_RUNNING", decision.code());
        assertEquals("PAUSED", decision.status());
    }

    @Test
    void globalSwitchBlocksEvenRunningPilot() {
        UUID businessId = UUID.randomUUID();
        PilotLaunchControlRepository controls = mock(PilotLaunchControlRepository.class);
        when(controls.findById(businessId))
                .thenReturn(Optional.of(control(businessId, PilotLaunchControl.Status.RUNNING)));

        ControlledPilotExternalEffectGuard guard =
                new ControlledPilotExternalEffectGuard(controls, false);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> guard.requireAllowed(
                        businessId,
                        ControlledPilotExternalEffectGuard.Effect.WHATSAPP));

        assertTrue(error.getMessage().contains("GLOBAL_KILL_SWITCH_ACTIVE"));
        assertTrue(error.getMessage().contains("WHATSAPP"));
    }

    private static PilotLaunchControl control(UUID businessId, PilotLaunchControl.Status status) {
        PilotLaunchControl control = new PilotLaunchControl();
        control.setBusinessId(businessId);
        control.setStatus(status);
        return control;
    }
}
