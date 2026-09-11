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

    public BusinessRequestController(BusinessRequestService service) { this.service = service; }

    @GetMapping
    public List<BusinessRequestDtos.Response> list() { return service.list(); }

    @PostMapping
    public BusinessRequestDtos.Response create(@Valid @RequestBody BusinessRequestDtos.Create input) {
        return service.create(input);
    }

    @PatchMapping("/{id}/status")
    public BusinessRequestDtos.Response setStatus(@PathVariable UUID id,
                                                  @RequestBody BusinessRequestDtos.UpdateStatus input) {
        return service.setStatus(id, input.status());
    }
}
