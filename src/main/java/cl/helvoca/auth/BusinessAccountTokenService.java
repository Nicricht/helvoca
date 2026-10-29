package cl.helvoca.auth;

import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Hash-only, transactional token boundary for business-owned accounts.
 * This service does not send messages, issue JWTs, or handle platform admins.
 */
@Service
public final class BusinessAccountTokenService {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final TenantDatabaseContext context;
    private final TransactionTemplate tx;

    public enum Purpose { RESET, VERIFY }
    public record Issued(String token, Instant expiresAt) {}
    public record Claim(UUID businessId, UUID userId) {}

    public BusinessAccountTokenService(JdbcTemplate jdbc,
                                       TenantDatabaseContext context,
                                       PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.context = context;
        this.tx = new TransactionTemplate(manager);
    }

    public Issued issue(UUID businessId, UUID userId, Purpose purpose) {
        if (businessId == null || userId == null || purpose == null) {
            throw new IllegalArgumentException("Business, user and purpose are required");
        }
        return context.callAsSystem(() -> tx.execute(status -> {
            List<UUID> owners = jdbc.query("""
                    SELECT id FROM app_user
                     WHERE id=? AND business_id=? AND active=true
                     FOR UPDATE
                    """, (rs, index) -> rs.getObject(1, UUID.class), userId, businessId);
            if (owners.size() != 1) {
                throw new IllegalArgumentException("Business account is not eligible");
            }

            jdbc.update("""
                    UPDATE business_account_token
                       SET revoked_at=clock_timestamp()
                     WHERE user_id=? AND purpose=?
                       AND consumed_at IS NULL AND revoked_at IS NULL
                    """, userId, purpose.name());
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            Instant expiry = Instant.now().plus(
                    purpose == Purpose.RESET ? 30 : 24 * 60,
                    ChronoUnit.MINUTES);
            jdbc.update("""
                    INSERT INTO business_account_token
                        (token_id,business_id,user_id,token_hash,purpose,expires_at)
                    VALUES (?,?,?,?,?,?)
                    """, UUID.randomUUID(), businessId, userId,
                    fingerprint(raw), purpose.name(), Timestamp.from(expiry));
            return new Issued(raw, expiry);
        }));
    }

    /**
     * Executes a credential mutation under the same database transaction and
     * locked token row as the irreversible one-time claim.
     */
    public void redeem(String raw, Purpose purpose, Consumer<Claim> mutation) {
        if (purpose == null || mutation == null || raw == null || !TOKEN.matcher(raw).matches()) {
            throw invalid();
        }
        context.runAsSystem(() -> tx.executeWithoutResult(status -> {
            List<Claim> claims = jdbc.query("""
                    SELECT token.business_id, token.user_id
                      FROM business_account_token token
                      JOIN app_user user_account
                        ON user_account.id=token.user_id
                       AND user_account.business_id=token.business_id
                     WHERE token.token_hash=? AND token.purpose=?
                       AND token.consumed_at IS NULL AND token.revoked_at IS NULL
                       AND token.expires_at > clock_timestamp()
                       AND user_account.active=true
                     FOR UPDATE OF token
                    """, (rs, index) -> new Claim(
                    rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)),
                    fingerprint(raw), purpose.name());
            if (claims.size() != 1) throw invalid();

            jdbc.update("""
                    UPDATE business_account_token SET consumed_at=clock_timestamp()
                     WHERE token_hash=?
                    """, fingerprint(raw));
            mutation.accept(claims.getFirst());
        }));
    }

    private static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
    }
}
