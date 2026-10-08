package cl.helvoca.request;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class BusinessRequestController {
    private final BusinessRequestService service;
    private final RequestLifecycleEventService lifecycleEvents;

    public BusinessRequestController(BusinessRequestService service,
                                     RequestLifecycleEventService lifecycleEvents) {
        this.service = service;
        this.lifecycleEvents = lifecycleEvents;
    }

    @GetMapping
    public List<BusinessRequestDtos.Response> list() { return service.list(); }

    @PostMapping
    public BusinessRequestDtos.Response create(@Valid @RequestBody BusinessRequestDtos.Create input) {
        return service.create(input);
    }

    @GetMapping("/{id}/history")
    public List<RequestLifecycleEventService.Transition> history(@PathVariable UUID id) {
        return lifecycleEvents.history(id);
    }

    @PatchMapping("/{id}/status")
    public BusinessRequestDtos.Response setStatus(@PathVariable UUID id,
                                                  @RequestBody BusinessRequestDtos.UpdateStatus input) {
        return service.setStatus(id, input.status());
    }
}
