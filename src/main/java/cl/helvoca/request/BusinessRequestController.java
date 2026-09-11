package cl.helvoca.request;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
public class BusinessRequestController {
    private final BusinessRequestService service;

    public BusinessRequestController(BusinessRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<BusinessRequestResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public BusinessRequestResponse create(@Valid @RequestBody BusinessRequestCreateRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public BusinessRequestResponse setStatus(@PathVariable UUID id,
                                             @Valid @RequestBody BusinessRequestStatusRequest request) {
        return service.setStatus(id, request);
    }
}
