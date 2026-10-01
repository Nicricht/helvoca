package cl.helvoca.platform;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformDemoSessionController {
    private final PlatformDemoSessionService service;
    private final PlatformDemoTimelineService timeline;

    public PlatformDemoSessionController(
            PlatformDemoSessionService service,
            PlatformDemoTimelineService timeline) {
        this.service = service;
        this.timeline = timeline;
    }

    @PostMapping("/demos/{profileId}/prepare")
    public PlatformDemoSessionResponse prepare(@PathVariable UUID profileId) {
        return service.prepare(profileId);
    }

    @GetMapping("/demo-sessions/current")
    public PlatformDemoSessionResponse current() {
        return service.current();
    }

    @GetMapping("/demo-sessions/{sessionId}")
    public PlatformDemoSessionResponse get(@PathVariable UUID sessionId) {
        return service.get(sessionId);
    }

    @GetMapping("/demo-sessions/{sessionId}/timeline")
    public PlatformDemoTimelineResponse timeline(@PathVariable UUID sessionId) {
        return timeline.timeline(sessionId);
    }
}
