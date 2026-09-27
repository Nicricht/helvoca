package cl.helvoca.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/pilot-preflight")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class PilotRealBusinessPreflightController {
    private final PilotRealBusinessPreflightService service;

    public PilotRealBusinessPreflightController(PilotRealBusinessPreflightService service) {
        this.service = service;
    }

    @GetMapping
    public PilotRealBusinessPreflightService.View current() {
        return service.current();
    }
}
