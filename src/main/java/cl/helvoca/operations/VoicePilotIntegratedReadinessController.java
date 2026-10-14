package cl.helvoca.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explicit business-admin GET only. Never enrolls a number, routes a call,
 * enables an external effect, or performs an unrequested startup probe.
 */
@RestController
@RequestMapping("/api/v1/operations")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class VoicePilotIntegratedReadinessController {
    private final VoicePilotIntegratedReadinessService integrated;

    public VoicePilotIntegratedReadinessController(VoicePilotIntegratedReadinessService integrated) {
        this.integrated = integrated;
    }

    @GetMapping("/voice-pilot-integrated-preflight")
    public VoicePilotIntegratedReadinessService.Result inspect(@RequestParam("phone") String phone) {
        return integrated.inspect(phone);
    }
}
