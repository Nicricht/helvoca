package cl.helvoca.platform;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/economics")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformCommercialEconomicsController {
    private final PlatformCommercialEconomicsService service;

    public PlatformCommercialEconomicsController(PlatformCommercialEconomicsService service) {
        this.service = service;
    }

    @GetMapping
    public PlatformCommercialEconomicsService.PortfolioEconomics portfolio() {
        return service.portfolio();
    }
}
