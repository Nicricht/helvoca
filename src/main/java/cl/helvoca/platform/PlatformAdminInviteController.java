package cl.helvoca.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "HELVOCA_PLATFORM_ADMIN_INVITES_ENABLED", havingValue = "true")
@RequestMapping("/api/v1/platform/admin-invitations")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminInviteController {
    public record InvitationRequest(@NotBlank @Size(max = 150) String name,
                                    @NotBlank @Email @Size(max = 180) String email) {}
    private final PlatformAdminInviteService service;

    public PlatformAdminInviteController(PlatformAdminInviteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PlatformAdminInviteService.InviteView> invite(
            @Valid @RequestBody InvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(service.invite(request.name(), request.email()));
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID invitationId) {
        service.revoke(invitationId);
    }
}
