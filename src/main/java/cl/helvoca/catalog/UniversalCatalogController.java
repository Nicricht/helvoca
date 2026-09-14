package cl.helvoca.catalog;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
public class UniversalCatalogController {
    private final UniversalCatalogService service;

    public UniversalCatalogController(UniversalCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<UniversalCatalogService.ItemView>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<UniversalCatalogService.ItemView> create(
            @RequestBody UniversalCatalogService.ItemInput input) {
        UniversalCatalogService.ItemView created = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/catalog/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<UniversalCatalogService.ItemView> update(
            @PathVariable UUID id,
            @RequestBody UniversalCatalogService.ItemInput input) {
        return ResponseEntity.ok(service.update(id, input));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
