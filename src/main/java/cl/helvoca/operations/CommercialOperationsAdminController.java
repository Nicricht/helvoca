package cl.helvoca.operations;

import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<CommercialOperationsAdminService.OrderView>> orders() {
        return ResponseEntity.ok(service.orders());
    }

    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<CommercialOperationsAdminService.OrderView> updateOrderStatus(
            @PathVariable UUID id,
            @RequestBody OrderStatusRequest request) {
        return ResponseEntity.ok(service.updateOrderStatus(id, request.status()));
    }

    @GetMapping("/quotes")
    public ResponseEntity<List<CommercialOperationsAdminService.QuoteView>> quotes() {
        return ResponseEntity.ok(service.quotes());
    }

    @GetMapping("/leads")
    public ResponseEntity<List<CommercialOperationsAdminService.LeadView>> leads() {
        return ResponseEntity.ok(service.leads());
    }

    public record OrderStatusRequest(BusinessOrder.Status status) {}
}
