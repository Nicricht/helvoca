package cl.helvoca.customer;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/commercial-timeline")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class CustomerCommercialTimelineController {
    private final CustomerCommercialTimelineService timeline;

    public CustomerCommercialTimelineController(CustomerCommercialTimelineService timeline) {
        this.timeline = timeline;
    }

    @GetMapping
    public CustomerCommercialTimelineService.TimelineResponse get(@PathVariable UUID customerId) {
        return timeline.get(customerId);
    }
}
