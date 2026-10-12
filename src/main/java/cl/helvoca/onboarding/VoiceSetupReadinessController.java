package cl.helvoca.onboarding;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only setup guidance, never a carrier action or activation endpoint. */
@RestController
@RequestMapping("/api/v1/onboarding/voice-readiness")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class VoiceSetupReadinessController {
    private final VoiceSetupReadinessService service;

    public VoiceSetupReadinessController(VoiceSetupReadinessService service) {
        this.service = service;
    }

    @GetMapping
    public VoiceSetupReadinessService.Guide current() {
        return service.current();
    }
}
