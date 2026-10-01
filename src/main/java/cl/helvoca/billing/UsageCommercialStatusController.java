package cl.helvoca.billing;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usage")
@PreAuthorize("hasAnyRole('BUSINESS_OWNER','BUSINESS_ADMIN')")
public class UsageCommercialStatusController {
    private final UsageCommercialStatusService service;

    public UsageCommercialStatusController(UsageCommercialStatusService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public UsageCommercialStatusService.UsageCommercialStatus current() {
        return service.currentForTenant();
    }
}
