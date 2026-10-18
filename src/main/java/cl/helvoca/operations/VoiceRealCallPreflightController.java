package cl.helvoca.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated inspection only. No call, provisioning, or pilot activation.
 */
@RestController
@RequestMapping("/api/v1/operations")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class VoiceRealCallPreflightController {
    private final VoiceRealCallPreflightService preflight;

    public VoiceRealCallPreflightController(VoiceRealCallPreflightService preflight) {
        this.preflight = preflight;
    }

    @GetMapping("/voice-real-call-preflight")
    public VoiceRealCallPreflightService.Result inspect(@RequestParam("phone") String phone) {
        return preflight.inspect(phone);
    }
}
