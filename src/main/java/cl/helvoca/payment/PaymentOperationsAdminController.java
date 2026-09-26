package cl.helvoca.payment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment-operations")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class PaymentOperationsAdminController {
    private final PaymentOperationsAdminService service;

    public PaymentOperationsAdminController(PaymentOperationsAdminService service) {
        this.service = service;
    }

    @GetMapping("/{operationId}/diagnostic")
    public PaymentOperationsAdminService.Diagnostic diagnostic(
            @PathVariable UUID operationId) {
        return service.diagnose(operationId);
    }

    @PostMapping("/{operationId}/reconcile")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PaymentOperationsAdminService.Diagnostic reconcile(
            @PathVariable UUID operationId) {
        return service.reconcile(operationId);
    }
}
