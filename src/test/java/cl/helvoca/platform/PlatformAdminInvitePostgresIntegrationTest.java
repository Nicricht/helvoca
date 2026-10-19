package cl.helvoca.platform;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class PlatformAdminInvitePostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired @Qualifier("migrationDataSource") DataSource ownerSource;
    @Autowired PlatformAdminInviteService service;
    @Autowired TenantDatabaseContext context;
    private JdbcTemplate owner;

    @BeforeEach
    void setup() {
        owner = new JdbcTemplate(ownerSource);
        owner.update("DELETE FROM platform_admin_invitation");
        owner.update("DELETE FROM user_role WHERE user_id IN (SELECT id FROM app_user WHERE email LIKE 'onboard-%@example.test')");
        owner.update("DELETE FROM app_user WHERE email LIKE 'onboard-%@example.test'");
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void firstInvitationIsHashOnlyAndSingleUseWithTenantlessIdentity() {
        String email = newEmail();
        String secret = newToken();
        UUID inviteId = service.bootstrap("First Human", email, secret);
        assertEquals(inviteId, service.bootstrap("First Human", email, secret));
        assertEquals(0, users(email));
        String hash = owner.queryForObject(
                "SELECT token_hash FROM platform_admin_invitation WHERE invitation_id = ?",
                String.class, inviteId);
        assertNotEquals(secret, hash);
        assertEquals(64, hash.length());

        UUID user = service.accept(secret, "IndependentPrivatePassword1");
        assertEquals(1, users(email));
        assertEquals(1, platformRoles(user));
        assertNull(owner.queryForObject("SELECT business_id FROM app_user WHERE id = ?",
                UUID.class, user));
        assertThrows(ResponseStatusException.class,
                () -> service.accept(secret, "IndependentPrivatePassword1"));
        assertNull(service.bootstrap("Another Human", newEmail(), newToken()),
                "A previously activated platform bootstrap must remain a no-op on restart");
    }

    @Test
    void secondAdministratorMustBeInvitedByAuthenticatedFirstHuman() {
        String email = newEmail();
        String secret = newToken();
        service.bootstrap("First Human", email, secret);
        UUID first = service.accept(secret, "FirstPrivatePassword123");
        String second = newEmail();
        assertThrows(AccessDeniedException.class,
                () -> service.invite("Second Human", second));

        asPlatform(first, email);
        PlatformAdminInviteService.InviteView invite =
                context.callAsSystem(() -> service.invite("Second Human", second));
        assertEquals(43, invite.token().length());
        assertEquals(0, users(second));
        UUID other = service.accept(invite.token(), "SecondPrivatePassword123");
        assertNotEquals(first, other);
        assertEquals(1, platformRoles(other));
        assertNull(owner.queryForObject("SELECT business_id FROM app_user WHERE id = ?",
                UUID.class, other));
        assertThrows(ResponseStatusException.class,
                () -> service.accept(invite.token(), "SecondPrivatePassword123"));
    }

    @Test
    void revocationAndExpiryBlockActivation() {
        String email = newEmail();
        String secret = newToken();
        service.bootstrap("First Human", email, secret);
        UUID first = service.accept(secret, "FirstPrivatePassword123");
        asPlatform(first, email);
        String toRevoke = newEmail();
        PlatformAdminInviteService.InviteView revoke =
                context.callAsSystem(() -> service.invite("Revoked", toRevoke));
        context.runAsSystem(() -> service.revoke(revoke.invitationId()));
        assertThrows(ResponseStatusException.class,
                () -> service.accept(revoke.token(), "SecondPrivatePassword123"));
        String toExpire = newEmail();
        PlatformAdminInviteService.InviteView expired =
                context.callAsSystem(() -> service.invite("Expired", toExpire));
        owner.update("UPDATE platform_admin_invitation SET expires_at = clock_timestamp() - interval '1 second' WHERE invitation_id=?",
                expired.invitationId());
        assertThrows(ResponseStatusException.class,
                () -> service.accept(expired.token(), "SecondPrivatePassword123"));
        assertEquals(0, users(toRevoke));
        assertEquals(0, users(toExpire));
    }


    @Test
    void expiredBootstrapRequiresOperatorToRotateInsteadOfSilentlyDoingNothing() {
        String email = newEmail();
        String token = newToken();
        UUID id = service.bootstrap("Authorized Human", email, token);
        assertNotNull(id);
        owner.update("UPDATE platform_admin_invitation SET expires_at = clock_timestamp() - interval '1 second' WHERE invitation_id=?", id);
        assertThrows(IllegalStateException.class,
                () -> service.bootstrap("Authorized Human", email, token),
                "An expired bootstrap invitation must explicitly fail rather than leave the operator believing setup succeeded");
        assertEquals(0, users(email));
    }

    @Test
    void malformedSecretsAndOperatorIdentityNeverCreateAnInvitation() {
        String email = newEmail();
        assertThrows(IllegalArgumentException.class, () -> service.bootstrap("First", email, "short"));
        assertThrows(IllegalArgumentException.class, () -> service.bootstrap(null, email, newToken()));
        assertThrows(IllegalArgumentException.class, () -> service.bootstrap(" ", email, newToken()));
        assertThrows(IllegalArgumentException.class, () -> service.bootstrap("n".repeat(151), email, newToken()));
        assertThrows(IllegalArgumentException.class, () -> service.bootstrap("First", null, newToken()));
        assertThrows(IllegalArgumentException.class, () -> service.bootstrap("First", "not-an-email", newToken()));
        assertThrows(IllegalArgumentException.class, () -> service.bootstrap("First", "x".repeat(181), newToken()));
        assertEquals(0, owner.queryForObject("SELECT COUNT(*) FROM platform_admin_invitation", Integer.class));
        assertThrows(ResponseStatusException.class, () -> service.accept(null, "GoodPassword123"));
        assertThrows(ResponseStatusException.class, () -> service.accept("broken", "GoodPassword123"));
        assertThrows(ResponseStatusException.class, () -> service.accept(newToken(), null));
        assertThrows(ResponseStatusException.class, () -> service.accept(newToken(), "short"));
        assertThrows(ResponseStatusException.class, () -> service.accept(newToken(), "x".repeat(73)));
    }

    @Test
    void unverifiedIdentityAndRevokedInviterCannotCreatePrivilegedAccounts() {
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Unverified", newEmail())));
        assertThrows(AccessDeniedException.class,
                () -> context.runAsSystem(() -> service.revoke(UUID.randomUUID())));
        String firstEmail = newEmail();
        String firstToken = newToken();
        service.bootstrap("First", firstEmail, firstToken);
        UUID first = service.accept(firstToken, "OnlyFirstKnows123");
        asPlatform(first, firstEmail);
        assertThrows(AccessDeniedException.class,
                () -> service.invite("Bypassed", newEmail()),
                "An authenticated admin must have explicit SYSTEM database scope");
        assertThrows(ResponseStatusException.class,
                () -> context.callAsSystem(() -> service.invite("Self", firstEmail)));
        String pendingEmail = newEmail();
        PlatformAdminInviteService.InviteView second =
                context.callAsSystem(() -> service.invite("Second", pendingEmail));
        assertThrows(ResponseStatusException.class,
                () -> context.callAsSystem(() -> service.invite("Second", pendingEmail)));
        owner.update("UPDATE app_user SET active = false WHERE id = ?", first);
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Third", newEmail())));
        assertThrows(ResponseStatusException.class,
                () -> service.accept(second.token(), "OnlySecondKnows123"),
                "A revoked platform inviter cannot mint a replacement privileged account");
        assertEquals(0, users(pendingEmail));
    }

    private void asPlatform(UUID id, String email) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("local-fixture", now, now.plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of("sub", id.toString(), "email", email, "roles", List.of("PLATFORM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
    }

    private int users(String email) {
        return owner.queryForObject("SELECT COUNT(*) FROM app_user WHERE email=?", Integer.class, email);
    }

    private int platformRoles(UUID user) {
        return owner.queryForObject("SELECT COUNT(*) FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='PLATFORM_ADMIN'",
                Integer.class, user);
    }

    private static String newEmail() {
        return "onboard-" + UUID.randomUUID() + "@example.test";
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
