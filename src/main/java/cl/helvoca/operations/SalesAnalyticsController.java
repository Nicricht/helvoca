package cl.helvoca.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commercial/analytics")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class SalesAnalyticsController {
    private final SalesAnalyticsService service;

    public SalesAnalyticsController(SalesAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    public SalesAnalyticsService.AnalyticsResponse get(
            @RequestParam(defaultValue = "30") int days) {
        return service.analytics(days);
    }
}
