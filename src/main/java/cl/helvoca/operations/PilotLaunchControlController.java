package cl.helvoca.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations/pilot-control")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class PilotLaunchControlController {
    private final PilotLaunchControlService service;

    public PilotLaunchControlController(PilotLaunchControlService service) {
        this.service = service;
    }

    @GetMapping
    public PilotLaunchControlService.View current() {
        return service.current();
    }

    @PutMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PilotLaunchControlService.View configure(
            @RequestBody PilotLaunchControlService.ConfigureRequest request) {
        return service.configure(request);
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PilotLaunchControlService.View start() {
        return service.start();
    }

    @PostMapping("/pause")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PilotLaunchControlService.View pause() {
        return service.pause();
    }

    @PostMapping("/resume")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PilotLaunchControlService.View resume() {
        return service.resume();
    }

    @PostMapping("/complete")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PilotLaunchControlService.View complete() {
        return service.complete();
    }
}
