package cl.helvoca.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/alerts")
public class InventoryAlertController {
    private final InventoryAlertService service;

    public InventoryAlertController(InventoryAlertService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<InventoryAlertService.AlertView>> listOpen() {
        return ResponseEntity.ok(service.listOpen());
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<InventoryAlertService.AlertView>> history() {
        return ResponseEntity.ok(service.history());
    }

    @PostMapping("/{alertId}/acknowledge")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<InventoryAlertService.AlertView> acknowledge(
            @PathVariable UUID alertId) {
        return ResponseEntity.ok(service.acknowledge(alertId));
    }
}
