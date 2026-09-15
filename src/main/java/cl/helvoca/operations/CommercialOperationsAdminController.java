package cl.helvoca.operations;

import cl.helvoca.delivery.BusinessDelivery;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/commercial")
public class CommercialOperationsAdminController {
    private final CommercialOperationsAdminService service;

    public CommercialOperationsAdminController(CommercialOperationsAdminService service) {
        this.service = service;
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<CommercialOperationsAdminService.OrderView>> orders() {
        return ResponseEntity.ok(service.orders());
    }

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<CommercialOperationsAdminService.OrderView> updateOrderStatus(
            @PathVariable UUID id,
            @RequestBody OrderStatusRequest request) {
        return ResponseEntity.ok(service.updateOrderStatus(id, request == null ? null : request.status()));
    }

    @GetMapping("/deliveries")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<CommercialOperationsAdminService.DeliveryView>> deliveries() {
        return ResponseEntity.ok(service.deliveries());
    }

    @PatchMapping("/deliveries/{id}/status")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<CommercialOperationsAdminService.DeliveryView> updateDeliveryStatus(
            @PathVariable UUID id,
            @RequestBody DeliveryStatusRequest request) {
        return ResponseEntity.ok(service.updateDeliveryStatus(id, request == null ? null : request.status()));
    }

    @GetMapping("/quotes")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<CommercialOperationsAdminService.QuoteView>> quotes() {
        return ResponseEntity.ok(service.quotes());
    }

    @GetMapping("/leads")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<CommercialOperationsAdminService.LeadView>> leads() {
        return ResponseEntity.ok(service.leads());
    }

    public record OrderStatusRequest(BusinessOrder.Status status) {}
    public record DeliveryStatusRequest(BusinessDelivery.Status status) {}
}
