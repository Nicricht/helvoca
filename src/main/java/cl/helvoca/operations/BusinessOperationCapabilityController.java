package cl.helvoca.operations;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/business-capabilities")
public class BusinessOperationCapabilityController {
    private final BusinessOperationCapabilityService service;

    public BusinessOperationCapabilityController(BusinessOperationCapabilityService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<CapabilityResponse> current() {
        return ResponseEntity.ok(new CapabilityResponse(service.current()));
    }

    @PutMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<CapabilityResponse> replace(@RequestBody CapabilityRequest request) {
        Set<BusinessOperationCapability> requested = request == null || request.capabilities() == null
                ? Set.of()
                : request.capabilities();
        return ResponseEntity.ok(new CapabilityResponse(service.replaceCurrent(requested)));
    }

    public record CapabilityRequest(Set<BusinessOperationCapability> capabilities) {}
    public record CapabilityResponse(Set<BusinessOperationCapability> capabilities) {}
}
