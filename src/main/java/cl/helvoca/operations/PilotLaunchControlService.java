package cl.helvoca.operations;

import cl.helvoca.security.TenantProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PilotLaunchControlService {
    private final PilotLaunchControlRepository controls;
    private final PilotReadinessService readiness;
    private final TenantProvider tenantProvider;

    public PilotLaunchControlService(PilotLaunchControlRepository controls,
                                     PilotReadinessService readiness,
                                     TenantProvider tenantProvider) {
        this.controls = controls;
        this.readiness = readiness;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public View current() {
        UUID businessId = tenantProvider.requireBusinessId();
        PilotLaunchControl control = controls.findById(businessId).orElseGet(() -> draft(businessId));
        return view(control, readiness.readiness());
    }

    @Transactional
    public View configure(ConfigureRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        PilotLaunchControl control = controls.findById(businessId).orElseGet(() -> draft(businessId));

        control.setResponsibleName(clean(request == null ? null : request.responsibleName()));
        control.setResponsibleContact(clean(request == null ? null : request.responsibleContact()));
        control.setGoal(clean(request == null ? null : request.goal()));
        control.setPlannedEndAt(request == null ? null : request.plannedEndAt());

        PilotReadinessService.Readiness ready = readiness.readiness();
        if (control.getStatus() != PilotLaunchControl.Status.RUNNING
                && control.getStatus() != PilotLaunchControl.Status.PAUSED
                && control.getStatus() != PilotLaunchControl.Status.COMPLETED) {
            control.setStatus(ready.ready() && configurationComplete(control)
                    ? PilotLaunchControl.Status.READY
                    : PilotLaunchControl.Status.DRAFT);
        }

        return view(controls.save(control), ready);
    }

    @Transactional
    public View start() {
        UUID businessId = tenantProvider.requireBusinessId();
        PilotLaunchControl control = requireControl(businessId);
        PilotReadinessService.Readiness ready = readiness.readiness();

        if (!ready.ready()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pilot cannot start while technical readiness has blockers");
        }
        if (!configurationComplete(control)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pilot requires responsible, contact, goal and planned end date");
        }
        if (control.getStatus() == PilotLaunchControl.Status.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed pilot cannot be restarted");
        }
        if (control.getStatus() == PilotLaunchControl.Status.RUNNING) {
            return view(control, ready);
        }
        if (control.getStatus() == PilotLaunchControl.Status.PAUSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paused pilot must be resumed");
        }

        control.setStatus(PilotLaunchControl.Status.RUNNING);
        if (control.getStartedAt() == null) control.setStartedAt(Instant.now());
        control.setCompletedAt(null);
        return view(controls.save(control), ready);
    }

    @Transactional
    public View pause() {
        UUID businessId = tenantProvider.requireBusinessId();
        PilotLaunchControl control = requireControl(businessId);
        if (control.getStatus() != PilotLaunchControl.Status.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a running pilot can be paused");
        }
        control.setStatus(PilotLaunchControl.Status.PAUSED);
        return view(controls.save(control), readiness.readiness());
    }

    @Transactional
    public View resume() {
        UUID businessId = tenantProvider.requireBusinessId();
        PilotLaunchControl control = requireControl(businessId);
        PilotReadinessService.Readiness ready = readiness.readiness();
        if (control.getStatus() != PilotLaunchControl.Status.PAUSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a paused pilot can be resumed");
        }
        if (!ready.ready()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pilot cannot resume while technical readiness has blockers");
        }
        control.setStatus(PilotLaunchControl.Status.RUNNING);
        return view(controls.save(control), ready);
    }

    @Transactional
    public View complete() {
        UUID businessId = tenantProvider.requireBusinessId();
        PilotLaunchControl control = requireControl(businessId);
        if (control.getStatus() != PilotLaunchControl.Status.RUNNING
                && control.getStatus() != PilotLaunchControl.Status.PAUSED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a running or paused pilot can be completed");
        }
        control.setStatus(PilotLaunchControl.Status.COMPLETED);
        control.setCompletedAt(Instant.now());
        return view(controls.save(control), readiness.readiness());
    }

    private PilotLaunchControl requireControl(UUID businessId) {
        return controls.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Configure the pilot before changing its lifecycle"));
    }

    private static PilotLaunchControl draft(UUID businessId) {
        PilotLaunchControl control = new PilotLaunchControl();
        control.setBusinessId(businessId);
        control.setStatus(PilotLaunchControl.Status.DRAFT);
        return control;
    }

    private static boolean configurationComplete(PilotLaunchControl control) {
        return control != null
                && present(control.getResponsibleName())
                && present(control.getResponsibleContact())
                && present(control.getGoal())
                && control.getPlannedEndAt() != null;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static View view(PilotLaunchControl control,
                             PilotReadinessService.Readiness readiness) {
        boolean configComplete = configurationComplete(control);
        boolean technicalReady = readiness != null && readiness.ready();

        List<String> blockers = new ArrayList<>();
        if (readiness != null && readiness.blockers() != null) blockers.addAll(readiness.blockers());
        if (!present(control.getResponsibleName())) blockers.add("Responsable del piloto");
        if (!present(control.getResponsibleContact())) blockers.add("Contacto del responsable");
        if (!present(control.getGoal())) blockers.add("Objetivo medible");
        if (control.getPlannedEndAt() == null) blockers.add("Fecha planificada de cierre");

        String decision = switch (control.getStatus()) {
            case RUNNING -> "RUNNING";
            case PAUSED -> "PAUSED";
            case COMPLETED -> "COMPLETED";
            case DRAFT, READY -> technicalReady && configComplete ? "GO" : "NO_GO";
        };

        boolean canStart = control.getStatus() == PilotLaunchControl.Status.READY
                && technicalReady
                && configComplete;
        boolean canPause = control.getStatus() == PilotLaunchControl.Status.RUNNING;
        boolean canResume = control.getStatus() == PilotLaunchControl.Status.PAUSED
                && technicalReady
                && configComplete;
        boolean canComplete = control.getStatus() == PilotLaunchControl.Status.RUNNING
                || control.getStatus() == PilotLaunchControl.Status.PAUSED;

        return new View(
                control.getStatus().name(),
                decision,
                technicalReady,
                configComplete,
                List.copyOf(blockers),
                control.getResponsibleName(),
                control.getResponsibleContact(),
                control.getGoal(),
                control.getPlannedEndAt(),
                control.getStartedAt(),
                control.getCompletedAt(),
                canStart,
                canPause,
                canResume,
                canComplete);
    }

    public record ConfigureRequest(
            String responsibleName,
            String responsibleContact,
            String goal,
            Instant plannedEndAt) {}

    public record View(
            String status,
            String launchDecision,
            boolean technicalReady,
            boolean configurationComplete,
            List<String> blockers,
            String responsibleName,
            String responsibleContact,
            String goal,
            Instant plannedEndAt,
            Instant startedAt,
            Instant completedAt,
            boolean canStart,
            boolean canPause,
            boolean canResume,
            boolean canComplete) {}
}
