package cl.helvoca.operations;

import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PilotRealBusinessPreflightService {
    private final PilotLaunchControlService launchControl;
    private final ControlledPilotExternalEffectGuard externalEffects;
    private final TenantProvider tenantProvider;

    public PilotRealBusinessPreflightService(PilotLaunchControlService launchControl,
                                             ControlledPilotExternalEffectGuard externalEffects,
                                             TenantProvider tenantProvider) {
        this.launchControl = launchControl;
        this.externalEffects = externalEffects;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public View current() {
        UUID businessId = tenantProvider.requireBusinessId();
        PilotLaunchControlService.View launch = launchControl.current();

        ControlledPilotExternalEffectGuard.Decision voice =
                externalEffects.evaluate(businessId, ControlledPilotExternalEffectGuard.Effect.VOICE);
        ControlledPilotExternalEffectGuard.Decision whatsapp =
                externalEffects.evaluate(businessId, ControlledPilotExternalEffectGuard.Effect.WHATSAPP);
        ControlledPilotExternalEffectGuard.Decision payment =
                externalEffects.evaluate(businessId, ControlledPilotExternalEffectGuard.Effect.PAYMENT);

        List<EffectView> effects = List.of(
                effect(voice),
                effect(whatsapp),
                effect(payment));

        boolean pilotManaged = effects.stream().allMatch(EffectView::pilotManaged);
        boolean allAllowed = effects.stream().allMatch(EffectView::allowed);
        boolean allBlocked = effects.stream().noneMatch(EffectView::allowed);
        boolean globallyBlocked = effects.stream().allMatch(item ->
                "GLOBAL_KILL_SWITCH_ACTIVE".equals(item.code()));

        String state = state(launch, pilotManaged, allAllowed, allBlocked, globallyBlocked);

        List<String> blockers = new ArrayList<>();
        if (launch.blockers() != null) blockers.addAll(launch.blockers());
        if (!pilotManaged) blockers.add("Pilot control is not enrolled for this business");
        if ("UNSAFE".equals(state)) blockers.add("External-effect guard is in an inconsistent state");

        boolean readyToStart = pilotManaged && launch.canStart() && allBlocked;
        boolean safeToEnableGlobalSwitch = pilotManaged
                && "RUNNING".equals(launch.status())
                && globallyBlocked;
        boolean live = pilotManaged
                && "RUNNING".equals(launch.status())
                && allAllowed;

        return new View(
                businessId,
                state,
                launch.status(),
                launch.launchDecision(),
                launch.technicalReady(),
                launch.configurationComplete(),
                readyToStart,
                safeToEnableGlobalSwitch,
                live,
                List.copyOf(blockers),
                effects);
    }

    private static String state(PilotLaunchControlService.View launch,
                                boolean pilotManaged,
                                boolean allAllowed,
                                boolean allBlocked,
                                boolean globallyBlocked) {
        if (!pilotManaged) return "NOT_ENROLLED";
        if ("COMPLETED".equals(launch.status())) return allBlocked ? "COMPLETED" : "UNSAFE";
        if ("PAUSED".equals(launch.status())) return allBlocked ? "PAUSED_SAFE" : "UNSAFE";
        if ("RUNNING".equals(launch.status())) {
            if (allAllowed) return "LIVE";
            if (globallyBlocked) return "RUNNING_BLOCKED";
            return allBlocked ? "RUNNING_BLOCKED" : "UNSAFE";
        }
        if (launch.canStart() && allBlocked) return "READY_SAFE";
        return allBlocked ? "NOT_READY" : "UNSAFE";
    }

    private static EffectView effect(ControlledPilotExternalEffectGuard.Decision decision) {
        return new EffectView(
                decision.effect().name(),
                decision.allowed(),
                decision.pilotManaged(),
                decision.code(),
                decision.status());
    }

    public record EffectView(
            String effect,
            boolean allowed,
            boolean pilotManaged,
            String code,
            String pilotStatus) {}

    public record View(
            UUID businessId,
            String state,
            String pilotStatus,
            String launchDecision,
            boolean technicalReady,
            boolean configurationComplete,
            boolean readyToStart,
            boolean safeToEnableGlobalSwitch,
            boolean live,
            List<String> blockers,
            List<EffectView> effects) {}
}
