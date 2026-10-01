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
    @PreAuthorize("hasAuthority('PERM_ORDERS_READ')")
    public ResponseEntity<List<CommercialOperationsAdminService.OrderView>> orders() {
        return ResponseEntity.ok(service.orders());
    }

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("hasAuthority('PERM_ORDERS_MANAGE')")
    public ResponseEntity<CommercialOperationsAdminService.OrderView> updateOrderStatus(
            @PathVariable UUID id,
            @RequestBody OrderStatusRequest request) {
        return ResponseEntity.ok(service.updateOrderStatus(id, request == null ? null : request.status()));
    }

    @PatchMapping("/orders/{id}/preparation-status")
    @PreAuthorize("hasAuthority('PERM_ORDERS_PREPARE')")
    public ResponseEntity<CommercialOperationsAdminService.OrderView> updateOrderPreparationStatus(
            @PathVariable UUID id,
            @RequestBody OrderStatusRequest request) {
        return ResponseEntity.ok(service.updateOrderPreparationStatus(
                id, request == null ? null : request.status()));
    }

    @GetMapping("/deliveries")
    @PreAuthorize("hasAuthority('PERM_DELIVERIES_READ')")
    public ResponseEntity<List<CommercialOperationsAdminService.DeliveryView>> deliveries() {
        return ResponseEntity.ok(service.deliveries());
    }

    @PatchMapping("/deliveries/{id}/status")
    @PreAuthorize("hasAuthority('PERM_DELIVERIES_MANAGE')")
    public ResponseEntity<CommercialOperationsAdminService.DeliveryView> updateDeliveryStatus(
            @PathVariable UUID id,
            @RequestBody DeliveryStatusRequest request) {
        return ResponseEntity.ok(service.updateDeliveryStatus(id, request == null ? null : request.status()));
    }

    @GetMapping("/quotes")
    @PreAuthorize("hasAuthority('PERM_QUOTES_READ')")
    public ResponseEntity<List<CommercialOperationsAdminService.QuoteView>> quotes() {
        return ResponseEntity.ok(service.quotes());
    }

    @PatchMapping("/quotes/{id}/status")
    @PreAuthorize("hasAuthority('PERM_QUOTES_MANAGE')")
    public ResponseEntity<CommercialOperationsAdminService.QuoteView> updateQuoteStatus(
            @PathVariable UUID id,
            @RequestBody QuoteStatusRequest request) {
        return ResponseEntity.ok(service.updateQuoteStatus(id, request == null ? null : request.status()));
    }

    @GetMapping("/leads")
    @PreAuthorize("hasAuthority('PERM_LEADS_READ')")
    public ResponseEntity<List<CommercialOperationsAdminService.LeadView>> leads() {
        return ResponseEntity.ok(service.leads());
    }

    @PatchMapping("/leads/{id}/status")
    @PreAuthorize("hasAuthority('PERM_LEADS_MANAGE')")
    public ResponseEntity<CommercialOperationsAdminService.LeadView> updateLeadStatus(
            @PathVariable UUID id,
            @RequestBody LeadStatusRequest request) {
        return ResponseEntity.ok(service.updateLeadStatus(id, request == null ? null : request.status()));
    }

    public record OrderStatusRequest(BusinessOrder.Status status) {}
    public record DeliveryStatusRequest(BusinessDelivery.Status status) {}
    public record QuoteStatusRequest(BusinessQuote.Status status) {}
    public record LeadStatusRequest(BusinessLead.Status status) {}
}
