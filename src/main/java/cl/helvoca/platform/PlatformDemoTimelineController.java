package cl.helvoca.platform;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/demo-sessions")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformDemoTimelineController {
    private final PlatformDemoTimelineService service;

    public PlatformDemoTimelineController(PlatformDemoTimelineService service) {
        this.service = service;
    }

    @GetMapping("/{sessionId}/timeline")
    public PlatformDemoTimelineResponse timeline(@PathVariable UUID sessionId) {
        return service.timeline(sessionId);
    }

    @GetMapping("/{sessionId}/result")
    public PlatformDemoTimelineResponse.ProofOfValue result(@PathVariable UUID sessionId) {
        return service.timeline(sessionId).proofOfValue();
    }
}
