package cl.helvoca.platform;

import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public final class PlatformAdminInviteService {
    private final JdbcTemplate jdbc;
    private final TenantDatabaseContext context;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    public record InviteView(UUID invitationId, String token, Instant expiresAt) {}
    private record Pending(UUID id, String email, String fingerprint, Instant expiry) {}
    private record Claim(String name, String email, String purpose, UUID issuer, Instant expiresAt) {}

    public PlatformAdminInviteService(JdbcTemplate jdbc, TenantDatabaseContext context,
                                      PasswordEncoder encoder, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.context = context;
        this.encoder = encoder;
        this.tx = new TransactionTemplate(manager);
    }

    static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public UUID bootstrap(String name, String email, String token) {
        if (!validToken(token)) throw new IllegalArgumentException("Invalid bootstrap invitation token");
        String normalizedEmail = email(email);
        String normalizedName = name(name);
        return context.callAsSystem(() -> tx.execute(status -> {
            jdbc.execute("SELECT pg_advisory_xact_lock(601849)");
            if (platformCount() != 0) return null; // one-time bootstrap stays closed across restarts
            if (userExists(normalizedEmail)) throw new IllegalStateException("Identity already registered");
            List<Pending> old = jdbc.query("""
                    SELECT invitation_id, email, token_hash, expires_at
                    FROM platform_admin_invitation
                    WHERE purpose='BOOTSTRAP' AND accepted_at IS NULL AND revoked_at IS NULL
                    FOR UPDATE
                    """, (rs, row) -> new Pending(
                    rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                    rs.getTimestamp(4).toInstant()));
            for (Pending pending : old) {
                if (normalizedEmail.equals(pending.email())
                        && fingerprint(token).equals(pending.fingerprint())
                        && Instant.now().isBefore(pending.expiry())) return pending.id();
                if (normalizedEmail.equals(pending.email())
                        && fingerprint(token).equals(pending.fingerprint())) {
                    // A retry using the expired secret is not an active invitation. Fail
                    // visibly so the operator must rotate the secret before proceeding.
                    throw new IllegalStateException("Bootstrap invitation expired; rotate secret");
                }
            }
            jdbc.update("""
                    UPDATE platform_admin_invitation SET revoked_at=clock_timestamp()
                    WHERE purpose='BOOTSTRAP' AND accepted_at IS NULL AND revoked_at IS NULL
                    """);
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO platform_admin_invitation
                      (invitation_id,name,email,token_hash,purpose,expires_at)
                    VALUES (?,?,?,?,'BOOTSTRAP',clock_timestamp() + INTERVAL '24 hours')
                    """, id, normalizedName, normalizedEmail, fingerprint(token));
            return id;
        }));
    }

    public InviteView invite(String name, String email) {
        UUID actor = authenticatedPlatformActor();
        String normalizedName = name(name);
        String normalizedEmail = email(email);
        if (actorEmail().equals(normalizedEmail))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Independent administrator required");
        return tx.execute(status -> {
            if (userExists(normalizedEmail))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity already registered");
            Integer pending = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM platform_admin_invitation
                     WHERE lower(email)=? AND accepted_at IS NULL AND revoked_at IS NULL
                    """, Integer.class, normalizedEmail);
            if (!Integer.valueOf(0).equals(pending))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation pending");
            byte[] entropy = new byte[32];
            random.nextBytes(entropy);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
            UUID id = UUID.randomUUID();
            Instant expiresAt = Instant.now().plusSeconds(86400);
            jdbc.update("""
                    INSERT INTO platform_admin_invitation
                    (invitation_id,name,email,token_hash,purpose,issued_by,expires_at)
                    VALUES (?,?,?,?,'INVITE',?,?)
                    """, id, normalizedName, normalizedEmail, fingerprint(token), actor,
                    java.sql.Timestamp.from(expiresAt));
            return new InviteView(id, token, expiresAt);
        });
    }

    public void revoke(UUID invitationId) {
        authenticatedPlatformActor();
        if (invitationId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        tx.executeWithoutResult(status -> {
            int updated = jdbc.update("""
                    UPDATE platform_admin_invitation SET revoked_at=clock_timestamp()
                    WHERE invitation_id=? AND accepted_at IS NULL AND revoked_at IS NULL
                    """, invitationId);
            if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation unavailable");
        });
    }

    public UUID accept(String token, String personalPassword) {
        if (!validToken(token) || personalPassword == null
                || personalPassword.length() < 10 || personalPassword.length() > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid invitation acceptance");
        return context.callAsSystem(() -> tx.execute(status -> {
            jdbc.execute("SELECT pg_advisory_xact_lock(601849)");
            List<Claim> claims = jdbc.query("""
                    SELECT name,email,purpose,issued_by,expires_at FROM platform_admin_invitation
                    WHERE token_hash=? AND accepted_at IS NULL AND revoked_at IS NULL
                    FOR UPDATE
                    """, (rs, row) -> new Claim(rs.getString(1), rs.getString(2),
                    rs.getString(3), rs.getObject(4, UUID.class), rs.getTimestamp(5).toInstant()), fingerprint(token));
            if (claims.isEmpty() || !Instant.now().isBefore(claims.get(0).expiresAt()))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation unavailable");
            Claim claim = claims.get(0);
            if ("BOOTSTRAP".equals(claim.purpose()) && platformCount() != 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Bootstrap is closed");
            if ("INVITE".equals(claim.purpose()) && !activePlatformAdmin(claim.issuer()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Inviter is no longer active");
            if (userExists(claim.email()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity already registered");
            UUID role = jdbc.queryForObject("SELECT id FROM role WHERE code='PLATFORM_ADMIN'", UUID.class);
            UUID user = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO app_user (id,business_id,name,email,password_hash,active)
                    VALUES (?,NULL,?,?,?,TRUE)
                    """, user, claim.name(), claim.email(), encoder.encode(personalPassword));
            jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES (?,?)", user, role);
            jdbc.update("""
                    UPDATE platform_admin_invitation
                    SET accepted_at=clock_timestamp(), accepted_user_id=?
                    WHERE token_hash=?
                    """, user, fingerprint(token));
            return user;
        }));
    }

    private UUID authenticatedPlatformActor() {
        if (context.currentOrDenied().mode() != TenantDatabaseContext.Mode.SYSTEM)
            throw new AccessDeniedException("Platform database scope required");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)
                || jwt.getClaimAsStringList("roles") == null
                || !jwt.getClaimAsStringList("roles").contains("PLATFORM_ADMIN")
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority())))
            throw new AccessDeniedException("Signed platform account required");
        UUID id;
        try { id = UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException | NullPointerException ex) {
            throw new AccessDeniedException("Invalid platform account");
        }
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM app_user u
                JOIN user_role ur ON ur.user_id=u.id
                JOIN role r ON r.id=ur.role_id
                WHERE u.id=? AND u.active AND u.business_id IS NULL
                  AND lower(u.email)=lower(?) AND r.code='PLATFORM_ADMIN'
                """, Integer.class, id, jwt.getClaimAsString("email"));
        if (!Integer.valueOf(1).equals(n)) throw new AccessDeniedException("Platform role no longer active");
        return id;
    }

    private String actorEmail() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return email(jwt.getClaimAsString("email"));
    }

    private boolean activePlatformAdmin(UUID id) {
        if (id == null) return false;
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM app_user u
                JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id
                WHERE u.id=? AND u.active AND u.business_id IS NULL
                    AND r.code='PLATFORM_ADMIN'
                """, Integer.class, id);
        return Integer.valueOf(1).equals(n);
    }

    private int platformCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM app_user u JOIN user_role ur ON ur.user_id=u.id
                JOIN role r ON r.id=ur.role_id WHERE r.code='PLATFORM_ADMIN'
                """, Integer.class);
    }

    private boolean userExists(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE lower(email)=?",
                Integer.class, email);
        return !Integer.valueOf(0).equals(count);
    }

    private static String email(String value) {
        if (value == null) throw new IllegalArgumentException("Missing email");
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.length() > 180 || !v.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
            throw new IllegalArgumentException("Invalid email");
        return v;
    }

    private static String name(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 150)
            throw new IllegalArgumentException("Invalid name");
        return value.trim();
    }
}
