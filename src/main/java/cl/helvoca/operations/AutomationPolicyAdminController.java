package cl.helvoca.operations;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/automation-policies")
public class AutomationPolicyAdminController {
    private final AutomationPolicyAdminService service;

    public AutomationPolicyAdminController(AutomationPolicyAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<AutomationPolicyAdminService.View>> current() {
        return ResponseEntity.ok(service.current());
    }

    @GetMapping("/{operationType}")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<AutomationPolicyAdminService.View> current(
            @PathVariable BusinessOperation.Type operationType) {
        return ResponseEntity.ok(service.current(operationType));
    }

    @PatchMapping("/{operationType}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<AutomationPolicyAdminService.View> patch(
            @PathVariable BusinessOperation.Type operationType,
            @RequestBody AutomationPolicyAdminService.Update request) {
        return ResponseEntity.ok(service.patch(operationType, request));
    }

    @DeleteMapping("/{operationType}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<AutomationPolicyAdminService.View> reset(
            @PathVariable BusinessOperation.Type operationType) {
        return ResponseEntity.ok(service.reset(operationType));
    }
}
