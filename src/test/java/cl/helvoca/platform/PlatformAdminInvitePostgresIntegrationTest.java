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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    @Autowired PasswordEncoder passwordEncoder;
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


    @Test
    void bootstrapRotationRevokesOldPendingSecretAndCreatesOnlyOneCurrentInvitation() {
        String oldEmail = newEmail();
        String oldSecret = newToken();
        UUID oldId = service.bootstrap("Former Candidate", oldEmail, oldSecret);
        assertNotNull(oldId);
        String newEmail = newEmail();
        String newSecret = newToken();
        UUID newId = service.bootstrap("Authorized Candidate", newEmail, newSecret);
        assertNotEquals(oldId, newId);
        assertEquals(1, owner.queryForObject(
                "SELECT COUNT(*) FROM platform_admin_invitation WHERE purpose='BOOTSTRAP' AND accepted_at IS NULL AND revoked_at IS NULL",
                Integer.class));
        assertThrows(ResponseStatusException.class,
                () -> service.accept(oldSecret, "FormerCandidatePassword123"));
        UUID user = service.accept(newSecret, "AuthorizedCandidatePassword123");
        assertEquals(1, platformRoles(user));
        assertEquals(0, users(oldEmail));
    }

    @Test
    void registrationCollisionCannotBootstrapOrReplaceExistingIdentity() {
        String email = newEmail();
        UUID preexisting = UUID.randomUUID();
        owner.update("INSERT INTO app_user (id,name,email,password_hash,active) VALUES (?,?,?,?,TRUE)",
                preexisting, "Existing Person", email, "already-present-hash");
        assertThrows(IllegalStateException.class,
                () -> service.bootstrap("No Privilege Promotion", email, newToken()));
        assertEquals(0, owner.queryForObject("SELECT COUNT(*) FROM platform_admin_invitation", Integer.class));
        assertEquals(0, platformRoles(preexisting));
    }

    @Test
    void invitationFailsIfTargetAccountWasRegisteredAfterIssue() {
        String firstEmail = newEmail();
        String firstSecret = newToken();
        service.bootstrap("First", firstEmail, firstSecret);
        UUID first = service.accept(firstSecret, "FirstPrivatePassword123");
        asPlatform(first, firstEmail);
        String email = newEmail();
        var invite = context.callAsSystem(() -> service.invite("Another", email));
        UUID registered = UUID.randomUUID();
        owner.update("INSERT INTO app_user (id,name,email,password_hash,active) VALUES (?,?,?,?,TRUE)",
                registered, "Already Registered", email, "existing-hash");
        assertThrows(ResponseStatusException.class,
                () -> service.accept(invite.token(), "AProperPersonalPassword123"));
        assertEquals(0, platformRoles(registered));
        assertEquals(0, owner.queryForObject(
                "SELECT COUNT(*) FROM platform_admin_invitation WHERE invitation_id=? AND accepted_at IS NOT NULL",
                Integer.class, invite.invitationId()));
    }

    @Test
    void platformActorMustStillMatchActiveDatabaseIdentityAndRole() {
        String email = newEmail();
        String token = newToken();
        service.bootstrap("First", email, token);
        UUID first = service.accept(token, "FirstPrivatePassword123");
        asPlatform(UUID.randomUUID(), email);
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Wrong Subject", newEmail())));
        asPlatform(first, newEmail());
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Wrong Email", newEmail())));
        asPlatform(first, email);
        owner.update("DELETE FROM user_role WHERE user_id = ?", first);
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Removed Role", newEmail())));
    }

    @Test
    void authorizedInviterCannotRevokeUnknownInvitationOrIssueMalformedInvites() {
        String email = newEmail();
        String token = newToken();
        service.bootstrap("First", email, token);
        UUID first = service.accept(token, "FirstPrivatePassword123");
        asPlatform(first, email);
        assertThrows(ResponseStatusException.class,
                () -> context.runAsSystem(() -> service.revoke(null)));
        assertThrows(ResponseStatusException.class,
                () -> context.runAsSystem(() -> service.revoke(UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class,
                () -> context.callAsSystem(() -> service.invite(null, newEmail())));
        assertThrows(IllegalArgumentException.class,
                () -> context.callAsSystem(() -> service.invite("Second", "invalid email")));
        assertThrows(IllegalArgumentException.class,
                () -> context.callAsSystem(() -> service.invite("Second", null)));
        assertEquals(0, owner.queryForObject(
                "SELECT COUNT(*) FROM platform_admin_invitation WHERE purpose='INVITE'",
                Integer.class));
    }


    @Test
    void reissuingBootstrapToSameEmailWithRotatedTokenRevokesTheOldOne() {
        String address = newEmail();
        String old = newToken();
        UUID oldId = service.bootstrap("First", address, old);
        UUID newId = service.bootstrap("First", address, newToken());
        assertNotEquals(oldId, newId);
        assertThrows(ResponseStatusException.class,
                () -> service.accept(old, "PrivatePass1234"));
    }

    @Test
    void existingAccountIsNeverInvitableToPlatformRole() {
        String firstEmail = newEmail();
        String secret = newToken();
        service.bootstrap("First", firstEmail, secret);
        UUID first = service.accept(secret, "PrivatePass1234");
        asPlatform(first, firstEmail);
        String other = newEmail();
        owner.update("INSERT INTO app_user(id,name,email,password_hash,active) VALUES (?,?,?,?,TRUE)",
                UUID.randomUUID(), "Existing", other, "fixture-hash");
        assertThrows(ResponseStatusException.class,
                () -> context.callAsSystem(() -> service.invite("Existing", other)));
    }

    @Test
    void bootstrapClaimCannotActivateAfterPlatformAdminExists() {
        String firstEmail = newEmail(), token = newToken();
        service.bootstrap("First", firstEmail, token);
        UUID other = UUID.randomUUID();
        UUID role = owner.queryForObject("SELECT id FROM role WHERE code='PLATFORM_ADMIN'", UUID.class);
        owner.update("INSERT INTO app_user(id,name,email,password_hash,active) VALUES (?,?,?,?,TRUE)",
                other, "Separate Admin", newEmail(), "fixture-hash");
        owner.update("INSERT INTO user_role(user_id,role_id) VALUES (?,?)", other, role);
        assertThrows(ResponseStatusException.class,
                () -> service.accept(token, "PrivatePass1234"));
        assertEquals(0, users(firstEmail));
    }

    @Test
    void signedPlatformActorRequiresMatchingJwtSubjectRolesAndAuthorities() {
        String address = newEmail(), secret = newToken();
        service.bootstrap("First", address, secret);
        UUID first = service.accept(secret, "PrivatePass1234");
        Instant now = Instant.now();
        var authority = new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN");

        Jwt noRoleClaim = new Jwt("test", now, now.plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of("sub", first.toString(), "email", address));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(noRoleClaim, List.of(authority)));
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Second", newEmail())));

        Jwt wrongRole = new Jwt("test", now, now.plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of("sub", first.toString(), "email", address, "roles", List.of("BUSINESS_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(wrongRole, List.of(authority)));
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Second", newEmail())));

        Jwt noAuthority = new Jwt("test", now, now.plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of("sub", first.toString(), "email", address, "roles", List.of("PLATFORM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(noAuthority, List.of()));
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Second", newEmail())));

        Jwt malformedSubject = new Jwt("test", now, now.plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of("sub", "invalid-uuid", "email", address, "roles", List.of("PLATFORM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(malformedSubject, List.of(authority)));
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Second", newEmail())));

        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken(
                        "not-a-platform-jwt", "ignored", "ROLE_PLATFORM_ADMIN"));
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Second", newEmail())));
    }


    @Test
    void nullPlatformIssuerMustFailClosedWithoutQueryingAnotherTenant() {
        Boolean permitted = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "activePlatformAdmin", (Object) null);
        assertFalse(permitted);
    }

    @Test
    void invalidOrAbsentPlatformJwtSubjectAndUnauthenticatedIdentityAreRejected() {
        String address = newEmail(), secret = newToken();
        service.bootstrap("First", address, secret);
        UUID first = service.accept(secret, "AnIndependentPassword123");
        Instant now = Instant.now();
        var role = new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN");

        Jwt missingSubject = new Jwt("fixture", now, now.plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of("email", address, "roles", List.of("PLATFORM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(missingSubject, List.of(role)));
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Second", newEmail())));

        var unauthenticated = new org.springframework.security.authentication.TestingAuthenticationToken(
                "unauthenticated", "irrelevant", "ROLE_PLATFORM_ADMIN");
        unauthenticated.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        assertThrows(AccessDeniedException.class,
                () -> context.callAsSystem(() -> service.invite("Second", newEmail())));

        asPlatform(first, address);
        assertNotNull(context.callAsSystem(() -> service.invite("Real Human", newEmail())));
    }

    @Test
    void operatorRecoveryIsHashOnlySingleUseAndPreservesTheExistingPlatformIdentity() {
        String address = newEmail();
        String bootstrapToken = newToken();
        service.bootstrap("Existing Platform Admin", address, bootstrapToken);
        UUID user = service.accept(bootstrapToken, "OriginalPrivatePassword123");
        String recoveryToken = newToken();

        UUID recoveryId = service.recover(address.toUpperCase(Locale.ROOT), recoveryToken);

        assertNotNull(recoveryId);
        assertEquals("RECOVERY", owner.queryForObject(
                "SELECT purpose FROM platform_admin_invitation WHERE invitation_id=?",
                String.class, recoveryId));
        String storedFingerprint = owner.queryForObject(
                "SELECT token_hash FROM platform_admin_invitation WHERE invitation_id=?",
                String.class, recoveryId);
        assertEquals(64, storedFingerprint.length());
        assertNotEquals(recoveryToken, storedFingerprint);
        assertTrue(owner.queryForObject(
                "SELECT expires_at <= clock_timestamp() + interval '30 minutes' FROM platform_admin_invitation WHERE invitation_id=?",
                Boolean.class, recoveryId));

        UUID recovered = service.accept(recoveryToken, "RecoveredPrivatePassword123");

        assertEquals(user, recovered);
        assertEquals(1, users(address));
        assertEquals(1, platformRoles(user));
        String passwordHash = owner.queryForObject(
                "SELECT password_hash FROM app_user WHERE id=?", String.class, user);
        assertTrue(passwordEncoder.matches("RecoveredPrivatePassword123", passwordHash));
        assertFalse(passwordEncoder.matches("OriginalPrivatePassword123", passwordHash));
        assertEquals(user, owner.queryForObject(
                "SELECT accepted_user_id FROM platform_admin_invitation WHERE invitation_id=?",
                UUID.class, recoveryId));
        assertThrows(ResponseStatusException.class,
                () -> service.accept(recoveryToken, "AnotherPrivatePassword123"));
    }

    @Test
    void operatorRecoveryRejectsMalformedOrIneligibleTargetsAndExpiredTokens() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recover(newEmail(), "short"));
        assertThrows(IllegalStateException.class,
                () -> service.recover(newEmail(), newToken()));

        String nonPlatform = newEmail();
        owner.update("INSERT INTO app_user(id,name,email,password_hash,active) VALUES (?,?,?,?,TRUE)",
                UUID.randomUUID(), "Ordinary User", nonPlatform, "fixture-hash");
        assertThrows(IllegalStateException.class,
                () -> service.recover(nonPlatform, newToken()));

        String address = newEmail();
        String bootstrapToken = newToken();
        service.bootstrap("Existing Platform Admin", address, bootstrapToken);
        UUID user = service.accept(bootstrapToken, "OriginalPrivatePassword123");
        String expiredToken = newToken();
        UUID recoveryId = service.recover(address, expiredToken);
        owner.update("UPDATE platform_admin_invitation SET expires_at=clock_timestamp() - interval '1 second' WHERE invitation_id=?",
                recoveryId);
        assertThrows(ResponseStatusException.class,
                () -> service.accept(expiredToken, "RecoveredPrivatePassword123"));
        String unchangedHash = owner.queryForObject(
                "SELECT password_hash FROM app_user WHERE id=?", String.class, user);
        assertTrue(passwordEncoder.matches("OriginalPrivatePassword123", unchangedHash));

        owner.update("UPDATE app_user SET active=FALSE WHERE id=?", user);
        assertThrows(IllegalStateException.class,
                () -> service.recover(address, newToken()));
    }

    @Test
    void rotatingRecoveryRevokesTheOldTokenAndKeepsExactlyOnePendingRecovery() {
        String address = newEmail();
        String bootstrapToken = newToken();
        service.bootstrap("Existing Platform Admin", address, bootstrapToken);
        UUID user = service.accept(bootstrapToken, "OriginalPrivatePassword123");
        String oldToken = newToken();
        UUID oldId = service.recover(address, oldToken);
        String currentToken = newToken();
        UUID currentId = service.recover(address, currentToken);

        assertNotEquals(oldId, currentId);
        assertEquals(1, owner.queryForObject("""
                SELECT COUNT(*) FROM platform_admin_invitation
                WHERE purpose='RECOVERY' AND accepted_at IS NULL AND revoked_at IS NULL
                """, Integer.class));
        assertThrows(ResponseStatusException.class,
                () -> service.accept(oldToken, "OldTokenMustNotWork123"));
        assertEquals(user, service.accept(currentToken, "CurrentRecoveryPassword123"));
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
