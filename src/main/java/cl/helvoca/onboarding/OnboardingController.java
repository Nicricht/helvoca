package cl.helvoca.onboarding;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/onboarding")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class OnboardingController {
    private final OnboardingService service;
    private final AutoOnboardingService autoOnboarding;

    public OnboardingController(OnboardingService service,
                                AutoOnboardingService autoOnboarding) {
        this.service = service;
        this.autoOnboarding = autoOnboarding;
    }

    @GetMapping("/status")
    public OnboardingStatusResponse status() {
        return service.status();
    }

    @PostMapping("/analyze")
    public AutoOnboardingProposal analyze(@Valid @RequestBody AutoOnboardingRequest request) {
        return autoOnboarding.analyze(request);
    }

    @PutMapping("/setup")
    public OnboardingStatusResponse setup(@Valid @RequestBody OnboardingSetupRequest request) {
        return service.setup(request);
    }
}
