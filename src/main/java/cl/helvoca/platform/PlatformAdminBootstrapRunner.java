package cl.helvoca.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Private opt-in setup of an invitation, never a privileged user or login. */
@Component
@ConditionalOnProperty(name = "HELVOCA_PLATFORM_BOOTSTRAP_ENABLED", havingValue = "true")
public final class PlatformAdminBootstrapRunner implements CommandLineRunner {
    private final PlatformAdminInviteService invitations;
    private final String name;
    private final String email;
    private final String token;

    public PlatformAdminBootstrapRunner(PlatformAdminInviteService invitations,
            @Value("${HELVOCA_PLATFORM_BOOTSTRAP_NAME:}") String name,
            @Value("${HELVOCA_PLATFORM_BOOTSTRAP_EMAIL:}") String email,
            @Value("${HELVOCA_PLATFORM_BOOTSTRAP_TOKEN:}") String token) {
        this.invitations = invitations;
        this.name = name;
        this.email = email;
        this.token = token;
    }

    @Override
    public void run(String... args) {
        invitations.bootstrap(name, email, token);
        // Never emit the invitation token or account identity into logs.
    }
}
