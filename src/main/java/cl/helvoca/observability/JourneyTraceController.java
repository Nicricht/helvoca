package cl.helvoca.observability;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/debug/journeys")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class JourneyTraceController {
    private final JourneyTraceService service;

    public JourneyTraceController(JourneyTraceService service) {
        this.service = service;
    }

    @GetMapping("/{identifier}")
    public JourneyTraceService.TraceView get(@PathVariable String identifier) {
        return service.get(identifier);
    }
}
