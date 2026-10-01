package cl.helvoca.platform;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformDemoSessionController {
    private final PlatformDemoSessionService service;

    public PlatformDemoSessionController(PlatformDemoSessionService service) {
        this.service = service;
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

    @PostMapping("/demo-sessions/{sessionId}/start")
    public PlatformDemoSessionResponse start(@PathVariable UUID sessionId) {
        return service.start(sessionId);
    }

    @PostMapping("/demo-sessions/{sessionId}/finish")
    public PlatformDemoSessionResponse finish(@PathVariable UUID sessionId) {
        return service.finish(sessionId);
    }

    @PostMapping("/demo-sessions/{sessionId}/abort")
    public PlatformDemoSessionResponse abort(@PathVariable UUID sessionId) {
        return service.abort(sessionId);
    }
}
