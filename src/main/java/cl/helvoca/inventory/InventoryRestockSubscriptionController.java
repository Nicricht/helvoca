package cl.helvoca.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/restock-subscriptions")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class InventoryRestockSubscriptionController {
    private final InventoryRestockSubscriptionService service;

    public InventoryRestockSubscriptionController(InventoryRestockSubscriptionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<InventoryRestockSubscriptionService.SubscriptionView>> list(
            @RequestParam(required = false) InventoryRestockSubscription.Status status) {
        return ResponseEntity.ok(service.list(status));
    }

    @PostMapping
    public ResponseEntity<InventoryRestockSubscriptionService.SubscriptionView> subscribe(
            @RequestBody InventoryRestockSubscriptionService.SubscribeRequest request) {
        return ResponseEntity.ok(service.subscribe(request));
    }

    @PostMapping("/{subscriptionId}/cancel")
    public ResponseEntity<InventoryRestockSubscriptionService.SubscriptionView> cancel(
            @PathVariable UUID subscriptionId) {
        return ResponseEntity.ok(service.cancel(subscriptionId));
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<InventoryRestockSubscriptionService.NotificationView>> pendingNotifications() {
        return ResponseEntity.ok(service.pendingNotifications());
    }
}
