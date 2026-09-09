package cl.helvoca.knowledge;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) { this.service = service; }

    @GetMapping
    public List<KnowledgeItemResponse> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/{id}")
    public KnowledgeItemResponse get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeItemResponse create(@Valid @RequestBody KnowledgeItemRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public KnowledgeItemResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody KnowledgeItemRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) { service.deactivate(id); }
}
