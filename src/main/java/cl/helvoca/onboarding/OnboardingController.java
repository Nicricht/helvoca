package cl.helvoca.onboarding;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/onboarding")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class OnboardingController {
    private final OnboardingService service;

    public OnboardingController(OnboardingService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public OnboardingStatusResponse status() {
        return service.status();
    }

    @PutMapping("/setup")
    public OnboardingStatusResponse setup(@Valid @RequestBody OnboardingSetupRequest request) {
        return service.setup(request);
    }
}
