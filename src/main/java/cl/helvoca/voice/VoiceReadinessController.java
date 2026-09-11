package cl.helvoca.voice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/voice")
public class VoiceReadinessController {
    private final VoiceReadinessService service;

    public VoiceReadinessController(VoiceReadinessService service) {
        this.service = service;
    }

    @GetMapping("/readiness")
    public VoiceReadinessResponse readiness() {
        return service.current();
    }
}
