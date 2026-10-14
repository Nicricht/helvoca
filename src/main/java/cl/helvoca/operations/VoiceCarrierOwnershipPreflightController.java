package cl.helvoca.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Explicit operator request only. No startup probe and no caller-side effects. */
@RestController
@RequestMapping("/api/v1/operations")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class VoiceCarrierOwnershipPreflightController {
    private final VoiceCarrierOwnershipPreflightService service;

    public VoiceCarrierOwnershipPreflightController(VoiceCarrierOwnershipPreflightService service) {
        this.service = service;
    }

    @GetMapping("/voice-carrier-ownership-preflight")
    public VoiceCarrierOwnershipPreflightService.Result inspect(@RequestParam("phone") String phone) {
        return service.inspect(phone);
    }
}
