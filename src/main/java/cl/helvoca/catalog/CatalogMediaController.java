package cl.helvoca.catalog;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/{catalogItemId}/media")
public class CatalogMediaController {
    private final CatalogMediaService service;

    public CatalogMediaController(CatalogMediaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public ResponseEntity<List<CatalogMediaService.MediaView>> list(@PathVariable UUID catalogItemId) {
        return ResponseEntity.ok(service.list(catalogItemId));
    }

    @PostMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<CatalogMediaService.MediaView> create(
            @PathVariable UUID catalogItemId,
            @RequestBody CatalogMediaService.MediaInput input) {
        CatalogMediaService.MediaView created = service.create(catalogItemId, input);
        return ResponseEntity
                .created(URI.create("/api/v1/catalog/" + catalogItemId + "/media/" + created.id()))
                .body(created);
    }

    @PutMapping("/{mediaId}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<CatalogMediaService.MediaView> update(
            @PathVariable UUID catalogItemId,
            @PathVariable UUID mediaId,
            @RequestBody CatalogMediaService.MediaInput input) {
        return ResponseEntity.ok(service.update(catalogItemId, mediaId, input));
    }

    @DeleteMapping("/{mediaId}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID catalogItemId,
            @PathVariable UUID mediaId) {
        service.deactivate(catalogItemId, mediaId);
        return ResponseEntity.noContent().build();
    }
}
