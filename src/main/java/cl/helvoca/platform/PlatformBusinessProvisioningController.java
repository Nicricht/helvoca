package cl.helvoca.platform;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/businesses")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformBusinessProvisioningController {
    private final PlatformBusinessProvisioningService service;

    public PlatformBusinessProvisioningController(PlatformBusinessProvisioningService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformBusinessProvisioningResponse provision(
            @Valid @RequestBody PlatformBusinessProvisioningRequest request) {
        return service.provision(request);
    }
}
