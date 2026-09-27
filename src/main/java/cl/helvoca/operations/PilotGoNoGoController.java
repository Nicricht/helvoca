package cl.helvoca.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/pilot-preflight")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class PilotGoNoGoController {
    private final PilotGoNoGoService service;

    public PilotGoNoGoController(PilotGoNoGoService service) {
        this.service = service;
    }

    @GetMapping
    public PilotGoNoGoService.View evaluate() {
        return service.evaluate();
    }
}
