package cl.helvoca.capability;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/capabilities")
public class BusinessCapabilityController {
    private final BusinessCapabilityService service;

    public BusinessCapabilityController(BusinessCapabilityService service) {
        this.service = service;
    }

    @GetMapping
    public BusinessCapabilityResponse current() {
        return service.current();
    }

    @PutMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public BusinessCapabilityResponse update(@Valid @RequestBody BusinessCapabilityUpdateRequest request) {
        return service.update(request);
    }
}
