package cl.helvoca.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public self-service entrypoint with no tenant or account identifiers. */
@RestController
@RequestMapping("/api/v1/auth/password-reset")
public final class BusinessAccountRecoveryController {
    public record Request(@NotBlank @Email @Size(max = 180) String email) {}
    public record Confirm(@NotBlank String token,
                          @NotBlank @Size(min = 10, max = 72) String newPassword) {}

    private final BusinessAccountRecoveryService recovery;

    public BusinessAccountRecoveryController(BusinessAccountRecoveryService recovery) {
        this.recovery = recovery;
    }

    @PostMapping("/request")
    public ResponseEntity<Void> request(@Valid @RequestBody Request input) {
        recovery.requestReset(input.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody Confirm input) {
        recovery.confirmReset(input.token(), input.newPassword());
        return ResponseEntity.noContent().build();
    }
}
