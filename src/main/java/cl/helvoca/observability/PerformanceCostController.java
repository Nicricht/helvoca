package cl.helvoca.observability;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/observability/performance-cost")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class PerformanceCostController {
    private final PerformanceCostService service;

    public PerformanceCostController(PerformanceCostService service) {
        this.service = service;
    }

    @GetMapping
    public PerformanceCostService.Snapshot snapshot(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.snapshot(from, to);
    }
}
