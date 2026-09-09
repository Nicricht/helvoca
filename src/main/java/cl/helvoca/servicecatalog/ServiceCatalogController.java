package cl.helvoca.servicecatalog;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {
    private final ServiceCatalogService service;

    public ServiceCatalogController(ServiceCatalogService service) { this.service = service; }

    @GetMapping
    public List<ServiceItemResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public ServiceItemResponse get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceItemResponse create(@Valid @RequestBody ServiceItemRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public ServiceItemResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceItemRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) { service.deactivate(id); }
}
