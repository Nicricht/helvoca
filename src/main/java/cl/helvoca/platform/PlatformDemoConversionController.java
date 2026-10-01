package cl.helvoca.platform;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/demo-sessions")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformDemoConversionController {
    private final PlatformDemoConversionService service;

    public PlatformDemoConversionController(PlatformDemoConversionService service) {
        this.service = service;
    }

    @PostMapping("/{sessionId}/convert-to-pilot")
    public PlatformDemoConversionResponse convert(
            @PathVariable UUID sessionId,
            @Valid @RequestBody PlatformDemoConversionRequest request) {
        return service.convert(sessionId, request);
    }
}
