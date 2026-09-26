package cl.helvoca.auth;

import cl.helvoca.user.AcceptInvitationRequest;
import cl.helvoca.user.TeamInvitationResponse;
import cl.helvoca.user.TeamInvitationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/invitations")
public class InvitationAuthController {
    private final TeamInvitationService service;

    public InvitationAuthController(TeamInvitationService service) {
        this.service = service;
    }

    @GetMapping("/{businessId}/{token}")
    public TeamInvitationResponse preview(@PathVariable UUID businessId,
                                          @PathVariable String token) {
        return service.preview(businessId, token);
    }

    @PostMapping("/{businessId}/{token}/accept")
    public LoginResponse accept(@PathVariable UUID businessId,
                                @PathVariable String token,
                                @Valid @RequestBody AcceptInvitationRequest request) {
        return service.accept(businessId, token, request);
    }
}
