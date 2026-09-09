package cl.helvoca.knowledge;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public List<KnowledgeItemResponse> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public KnowledgeItemResponse get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public KnowledgeItemResponse create(@Valid @RequestBody KnowledgeItemRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public KnowledgeItemResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody KnowledgeItemRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public void deactivate(@PathVariable UUID id) { service.deactivate(id); }
}
