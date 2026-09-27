package cl.helvoca.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/{catalogItemId}/variants")
public class InventoryVariantController {
    private final InventoryVariantService service;

    public InventoryVariantController(InventoryVariantService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<InventoryVariantService.VariantView>> list(
            @PathVariable UUID catalogItemId) {
        return ResponseEntity.ok(service.list(catalogItemId));
    }

    @PostMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<InventoryVariantService.VariantView> create(
            @PathVariable UUID catalogItemId,
            @RequestBody InventoryVariantService.VariantInput input) {
        InventoryVariantService.VariantView created = service.create(catalogItemId, input);
        return ResponseEntity.created(URI.create(
                "/api/v1/inventory/" + catalogItemId + "/variants/" + created.id())).body(created);
    }

    @PutMapping("/{variantId}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<InventoryVariantService.VariantView> update(
            @PathVariable UUID catalogItemId,
            @PathVariable UUID variantId,
            @RequestBody InventoryVariantService.VariantInput input) {
        return ResponseEntity.ok(service.update(catalogItemId, variantId, input));
    }

    @PostMapping("/{variantId}/adjustments")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<InventoryVariantService.VariantView> adjust(
            @PathVariable UUID catalogItemId,
            @PathVariable UUID variantId,
            @RequestBody InventoryVariantService.AdjustmentInput input) {
        return ResponseEntity.ok(service.adjust(catalogItemId, variantId, input));
    }

    @PostMapping("/{variantId}/deactivate")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<InventoryVariantService.VariantView> deactivate(
            @PathVariable UUID catalogItemId,
            @PathVariable UUID variantId) {
        return ResponseEntity.ok(service.deactivate(catalogItemId, variantId));
    }

    @GetMapping("/{variantId}/movements")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<InventoryVariantService.VariantMovementView>> history(
            @PathVariable UUID catalogItemId,
            @PathVariable UUID variantId) {
        return ResponseEntity.ok(service.history(catalogItemId, variantId));
    }
}
