package cl.helvoca.auth;

import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Anonymous, non-enumerating business credential recovery. Never issues JWTs. */
@Service
public final class BusinessAccountRecoveryService {
    private final JdbcTemplate jdbc;
    private final TenantDatabaseContext context;
    private final BusinessAccountTokenService tokens;
    private final BusinessAccountMailService mail;
    private final PasswordEncoder encoder;

    private record Owner(UUID businessId, UUID userId) {}

    public BusinessAccountRecoveryService(JdbcTemplate jdbc,
                                          TenantDatabaseContext context,
                                          BusinessAccountTokenService tokens,
                                          BusinessAccountMailService mail,
                                          PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.context = context;
        this.tokens = tokens;
        this.mail = mail;
        this.encoder = encoder;
    }

    /** No caller can distinguish an absent, inactive, platform or disabled account. */
    public void requestReset(String email) {
        if (!mail.isEnabled() || email == null || email.isBlank()) return;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        List<Owner> eligible = context.callAsSystem(() -> jdbc.query("""
                SELECT business_id, id FROM app_user
                 WHERE lower(email)=? AND active=true AND business_id IS NOT NULL
                """, (rs, row) -> new Owner(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), normalized));
        if (eligible.size() != 1) return;

        Owner owner = eligible.getFirst();
        BusinessAccountTokenService.Issued issued = tokens.issue(
                owner.businessId(), owner.userId(), BusinessAccountTokenService.Purpose.RESET);
        try {
            mail.sendPasswordReset(normalized, issued.token());
        } catch (RuntimeException deliveryUnavailable) {
            // Fail closed to external callers: SMTP status must not expose whether
            // a business identity exists. Token remains hashed and will expire.
        }
    }

    public void confirmReset(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 10 || newPassword.length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password length");
        }
        String hash = encoder.encode(newPassword);
        tokens.redeem(rawToken, BusinessAccountTokenService.Purpose.RESET, claim -> {
            int updated = jdbc.update("""
                    UPDATE app_user SET password_hash=?
                     WHERE id=? AND business_id=? AND active=true
                    """, hash, claim.userId(), claim.businessId());
            if (updated != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
            }
        });
    }
}
