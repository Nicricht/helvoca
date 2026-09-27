package cl.helvoca.platform;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/certification-runs")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformCertificationRunController {
    private final PlatformCertificationRunService service;

    public PlatformCertificationRunController(PlatformCertificationRunService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformCertificationRunResponse create(
            @Valid @RequestBody PlatformCertificationRunRequest request,
            Authentication authentication) {
        return service.create(request.runId(), authentication == null ? null : authentication.getName());
    }

    @GetMapping("/{runId}")
    public PlatformCertificationRunResponse get(@PathVariable String runId) {
        return service.get(runId);
    }
}
