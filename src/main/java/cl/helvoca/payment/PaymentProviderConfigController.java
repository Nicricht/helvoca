package cl.helvoca.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment-provider")
public class PaymentProviderConfigController {
    private final PaymentProviderConfigService service;

    public PaymentProviderConfigController(PaymentProviderConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<PaymentProviderConfigService.View> current() {
        return ResponseEntity.ok(service.current());
    }

    @GetMapping("/managed-sandbox")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<PaymentProviderConfigService.ManagedSandboxView> managedSandbox() {
        return ResponseEntity.ok(service.managedSandbox());
    }

    @PostMapping("/managed-sandbox/enable")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<PaymentProviderConfigService.ManagedSandboxView> enableManagedSandbox() {
        return ResponseEntity.ok(service.enableManagedSandbox());
    }

    @PostMapping("/managed-sandbox/disable")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<PaymentProviderConfigService.ManagedSandboxView> disableManagedSandbox() {
        return ResponseEntity.ok(service.disableManagedSandbox());
    }

    @PutMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<PaymentProviderConfigService.View> replace(
            @RequestBody PaymentProviderConfigService.Update request) {
        return ResponseEntity.ok(service.replace(request));
    }
}
