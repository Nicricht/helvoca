package cl.helvoca.platform;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/demos/readiness")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformDemoReadinessController {
    private final PlatformDemoReadinessService service;

    public PlatformDemoReadinessController(PlatformDemoReadinessService service) {
        this.service = service;
    }

    @GetMapping
    public PlatformDemoReadinessResponse readiness() {
        return service.readiness();
    }
}
