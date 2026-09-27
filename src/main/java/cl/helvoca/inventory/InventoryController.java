package cl.helvoca.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<InventoryService.StockView>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{catalogItemId}")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<InventoryService.StockView> get(@PathVariable UUID catalogItemId) {
        return ResponseEntity.ok(service.get(catalogItemId));
    }

    @PutMapping("/{catalogItemId}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<InventoryService.StockView> configure(
            @PathVariable UUID catalogItemId,
            @RequestBody InventoryService.ConfigureInput input) {
        return ResponseEntity.ok(service.configure(catalogItemId, input));
    }

    @PostMapping("/{catalogItemId}/adjustments")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<InventoryService.StockView> adjust(
            @PathVariable UUID catalogItemId,
            @RequestBody InventoryService.AdjustmentInput input) {
        return ResponseEntity.ok(service.adjust(catalogItemId, input));
    }

    @PostMapping("/{catalogItemId}/reservations")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<InventoryService.ReservationView> reserve(
            @PathVariable UUID catalogItemId,
            @RequestBody InventoryService.ReservationInput input) {
        return ResponseEntity.ok(service.reserve(catalogItemId, input));
    }

    @PostMapping("/reservations/{reservationId}/release")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<InventoryService.ReservationView> release(
            @PathVariable UUID reservationId,
            @RequestBody(required = false) NoteInput input) {
        return ResponseEntity.ok(service.release(reservationId, input == null ? null : input.note()));
    }

    @PostMapping("/reservations/{reservationId}/consume")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<InventoryService.ReservationView> consume(
            @PathVariable UUID reservationId,
            @RequestBody(required = false) NoteInput input) {
        return ResponseEntity.ok(service.consume(reservationId, input == null ? null : input.note()));
    }

    @GetMapping("/{catalogItemId}/movements")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<InventoryService.MovementView>> history(@PathVariable UUID catalogItemId) {
        return ResponseEntity.ok(service.history(catalogItemId));
    }

    public record NoteInput(String note) {}
}
