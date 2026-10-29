package cl.helvoca.auth;

import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Tenant-owned identity confirmation, separate from platform-admin access. */
@Service
public final class BusinessAccountVerificationService {
    private final JdbcTemplate jdbc;
    private final TenantDatabaseContext context;
    private final BusinessAccountTokenService tokens;
    private final BusinessAccountMailService mail;

    private record Target(UUID businessId, UUID userId) {}

    public BusinessAccountVerificationService(JdbcTemplate jdbc,
                                              TenantDatabaseContext context,
                                              BusinessAccountTokenService tokens,
                                              BusinessAccountMailService mail) {
        this.jdbc = jdbc;
        this.context = context;
        this.tokens = tokens;
        this.mail = mail;
    }

    public boolean isRequired() { return mail.isVerificationRequired(); }

    /**
     * The verification column is deliberately NOT mapped on the JPA AppUser
     * entity: the rollback contract must boot this binary on a pre-V108 schema
     * when the feature flag is off. Only explicit verification paths read it.
     */
    public boolean isVerified(UUID businessId, UUID userId) {
        if (businessId == null || userId == null) return false;
        Boolean verified = context.callAsSystem(() -> jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM app_user
                     WHERE id=? AND business_id=? AND active=true
                       AND email_verified_at IS NOT NULL
                )
                """, Boolean.class, userId, businessId));
        return Boolean.TRUE.equals(verified);
    }

    /** Invoked only after possession-bound registration or team invitation. */
    public void markTrustedPossession(UUID businessId, UUID userId) {
        if (businessId == null || userId == null) {
            throw new IllegalArgumentException("Business and user identity required");
        }
        context.runAsSystem(() -> {
            int marked = jdbc.update("""
                    UPDATE app_user SET email_verified_at=clock_timestamp()
                     WHERE id=? AND business_id=? AND active=true
                       AND email_verified_at IS NULL
                    """, userId, businessId);
            if (marked != 1) {
                throw new IllegalStateException("Trusted business identity could not be marked verified");
            }
        });
    }

    /**
     * Only a committed signup may send a usable link. A rolled-back tenant
     * registration never produces external email. Delivery failures do not
     * pretend to verify the address; resend remains available.
     */
    public void scheduleRegistrationMessage(UUID businessId, UUID userId, String email) {
        if (!isRequired()) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Email confirmation requires an active registration transaction");
        }
        BusinessAccountTokenService.Issued issued = tokens.issue(
                businessId, userId, BusinessAccountTokenService.Purpose.VERIFY);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendSafely(email, issued.token());
            }
        });
    }

    /** Always identical HTTP response for absent, disabled, inactive or verified users. */
    public void resend(String email) {
        if (!mail.isEnabled() || !isRequired() || email == null || email.isBlank()) return;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        List<Target> eligible = context.callAsSystem(() -> jdbc.query("""
                SELECT business_id, id FROM app_user
                 WHERE lower(email)=? AND active=true AND business_id IS NOT NULL
                   AND email_verified_at IS NULL
                """, (rs, row) -> new Target(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), normalized));
        if (eligible.size() != 1) return;

        Target account = eligible.getFirst();
        BusinessAccountTokenService.Issued issued = tokens.issue(
                account.businessId(), account.userId(), BusinessAccountTokenService.Purpose.VERIFY);
        sendSafely(normalized, issued.token());
    }

    public void confirm(String token) {
        tokens.redeem(token, BusinessAccountTokenService.Purpose.VERIFY, claim -> {
            int updated = jdbc.update("""
                    UPDATE app_user SET email_verified_at=clock_timestamp()
                     WHERE id=? AND business_id=? AND active=true
                       AND email_verified_at IS NULL
                    """, claim.userId(), claim.businessId());
            if (updated != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
            }
        });
    }

    private void sendSafely(String email, String rawToken) {
        try {
            mail.sendVerification(email, rawToken);
        } catch (RuntimeException unavailable) {
            // Do not leak account existence, recipient identity or raw token.
            // The pending user may explicitly request another email later.
        }
    }
}
