package cl.helvoca.onboarding;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding/guide")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class BusinessActivationGuideController {
    private final BusinessActivationGuideService service;

    public BusinessActivationGuideController(BusinessActivationGuideService service) {
        this.service = service;
    }

    @GetMapping
    public BusinessActivationGuideService.Guide current() {
        return service.current();
    }
}
