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

@RestController
@RequestMapping("/api/v1/auth/email")
public final class BusinessAccountVerificationController {
    public record Address(@NotBlank @Email @Size(max = 180) String email) {}
    public record Token(@NotBlank String token) {}

    private final BusinessAccountVerificationService verification;

    public BusinessAccountVerificationController(BusinessAccountVerificationService verification) {
        this.verification = verification;
    }

    @PostMapping("/resend")
    public ResponseEntity<Void> resend(@Valid @RequestBody Address input) {
        verification.resend(input.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody Token input) {
        verification.confirm(input.token());
        return ResponseEntity.noContent().build();
    }
}
