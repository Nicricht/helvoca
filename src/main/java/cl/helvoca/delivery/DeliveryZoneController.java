package cl.helvoca.delivery;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery-zones")
public class DeliveryZoneController {
    private final DeliveryZoneService service;

    public DeliveryZoneController(DeliveryZoneService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<DeliveryZoneService.ZoneView>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<DeliveryZoneService.ZoneView> create(@RequestBody DeliveryZoneService.ZoneInput input) {
        DeliveryZoneService.ZoneView created = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/delivery-zones/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<DeliveryZoneService.ZoneView> update(
            @PathVariable UUID id,
            @RequestBody DeliveryZoneService.ZoneInput input) {
        return ResponseEntity.ok(service.update(id, input));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
