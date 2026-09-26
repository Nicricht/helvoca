package cl.helvoca.onboarding;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/onboarding/activation")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class PilotActivationChecklistController {
    private final PilotActivationChecklistService service;

    public PilotActivationChecklistController(PilotActivationChecklistService service) {
        this.service = service;
    }

    @GetMapping
    public PilotActivationChecklistService.View current() {
        return service.current();
    }

    @PutMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PilotActivationChecklistService.View update(
            @RequestBody PilotActivationChecklistService.ConfirmationRequest request) {
        return service.update(request);
    }
}
