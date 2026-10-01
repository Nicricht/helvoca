package cl.helvoca.platform;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/demos")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformDemoProfileController {
    private final PlatformDemoProfileService service;

    public PlatformDemoProfileController(PlatformDemoProfileService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlatformDemoProfileResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformDemoProfileResponse create(@Valid @RequestBody PlatformDemoProfileRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PlatformDemoProfileResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody PlatformDemoProfileRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
