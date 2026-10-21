package cl.helvoca.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Operator-only, default-off issuance of a single-use recovery claim. */
@Component
@ConditionalOnProperty(name = "HELVOCA_PLATFORM_ADMIN_RECOVERY_ENABLED", havingValue = "true")
public final class PlatformAdminRecoveryRunner implements CommandLineRunner {
    private final PlatformAdminInviteService invitations;
    private final String email;
    private final String token;

    public PlatformAdminRecoveryRunner(PlatformAdminInviteService invitations,
            @Value("${HELVOCA_PLATFORM_ADMIN_RECOVERY_EMAIL:}") String email,
            @Value("${HELVOCA_PLATFORM_ADMIN_RECOVERY_TOKEN:}") String token) {
        this.invitations = invitations;
        this.email = email;
        this.token = token;
    }

    @Override
    public void run(String... args) {
        invitations.recover(email, token);
        // Never emit the recovery token or account identity into logs.
    }
}

