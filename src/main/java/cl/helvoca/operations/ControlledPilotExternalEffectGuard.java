package cl.helvoca.operations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Final safety boundary for real external effects during a controlled pilot.
 *
 * Non-pilot tenants preserve their existing behavior. Once a tenant has a
 * pilot_launch_control record, real effects require both the global opt-in and
 * a RUNNING pilot state.
 */
@Service
public class ControlledPilotExternalEffectGuard {
    public enum Effect { VOICE, WHATSAPP, PAYMENT }

    private final PilotLaunchControlRepository controls;
    private final boolean externalEffectsEnabled;

    public ControlledPilotExternalEffectGuard(
            PilotLaunchControlRepository controls,
            @Value("${HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED:false}") boolean externalEffectsEnabled) {
        this.controls = controls;
        this.externalEffectsEnabled = externalEffectsEnabled;
    }

    @Transactional(readOnly = true)
    public Decision evaluate(UUID businessId, Effect effect) {
        if (businessId == null) throw new IllegalArgumentException("Business id is required");
        if (effect == null) throw new IllegalArgumentException("Pilot external effect is required");

        PilotLaunchControl control = controls.findById(businessId).orElse(null);
        if (control == null) {
            return new Decision(true, false, "NOT_PILOT_MANAGED", null, effect);
        }

        String status = control.getStatus() == null
                ? PilotLaunchControl.Status.DRAFT.name()
                : control.getStatus().name();

        if (!externalEffectsEnabled) {
            return new Decision(false, true, "GLOBAL_KILL_SWITCH_ACTIVE", status, effect);
        }
        if (control.getStatus() != PilotLaunchControl.Status.RUNNING) {
            return new Decision(false, true, "PILOT_NOT_RUNNING", status, effect);
        }
        return new Decision(true, true, "ALLOWED", status, effect);
    }

    public void requireAllowed(UUID businessId, Effect effect) {
        Decision decision = evaluate(businessId, effect);
        if (!decision.allowed()) {
            throw new IllegalStateException(
                    "Controlled pilot external effect blocked: "
                            + decision.code()
                            + " effect="
                            + decision.effect()
                            + " status="
                            + decision.status());
        }
    }

    public record Decision(
            boolean allowed,
            boolean pilotManaged,
            String code,
            String status,
            Effect effect) {}
}
