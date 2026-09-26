package cl.helvoca.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/invitations")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class TeamInvitationAdminController {
    private final TeamInvitationService service;

    public TeamInvitationAdminController(TeamInvitationService service) {
        this.service = service;
    }

    @GetMapping
    public List<TeamInvitationResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamInvitationResponse create(@Valid @RequestBody InviteUserRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID invitationId) {
        service.revoke(invitationId);
    }
}
