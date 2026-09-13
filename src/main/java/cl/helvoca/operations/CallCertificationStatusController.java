package cl.helvoca.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/certification")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class CallCertificationStatusController {
    private final CallCertificationStatusService service;

    public CallCertificationStatusController(CallCertificationStatusService service) {
        this.service = service;
    }

    @GetMapping
    public CallCertificationStatusService.Status current() {
        return service.current();
    }
}
