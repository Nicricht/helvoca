package cl.helvoca.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "HELVOCA_PLATFORM_ADMIN_INVITES_ENABLED", havingValue = "true")
@RequestMapping("/api/v1/auth/invitations/platform")
public final class PlatformAdminInviteAcceptanceController {
    public record Acceptance(@NotBlank @Size(min = 43, max = 43) String token,
                             @NotBlank @Size(min = 10, max = 72) String password) {}
    private final PlatformAdminInviteService service;

    public PlatformAdminInviteAcceptanceController(PlatformAdminInviteService service) {
        this.service = service;
    }

    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@Valid @RequestBody Acceptance request) {
        service.accept(request.token(), request.password());
    }
}
