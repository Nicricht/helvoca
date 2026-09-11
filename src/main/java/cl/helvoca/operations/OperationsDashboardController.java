package cl.helvoca.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class OperationsDashboardController {
    private final OperationsDashboardService service;

    public OperationsDashboardController(OperationsDashboardService service) { this.service = service; }

    @GetMapping("/dashboard")
    public OperationsDashboardService.Dashboard dashboard() { return service.dashboard(); }
}
