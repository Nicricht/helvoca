package cl.helvoca.phone;

import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Phase 5 ONLY: a system-scoped, synchronous, carrier-attested compare-and-set.
 * There is deliberately NO controller, scheduler, purchase or call activation.
 *
 * Callers MUST already hold a trusted explicit SYSTEM DB scope. This class
 * cannot elevate a tenant session; phase 6 will enforce the platform-human
 * authentication, dual approval and audit before granting that scope.
 */
@Component
public final class TwilioExistingEnrollmentReconciler {
    private final TenantDatabaseContext context;
    private final PhoneNumberRepository phones;
    private final TwilioExistingEnrollmentPlanner planner;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public TwilioExistingEnrollmentReconciler(
            TenantDatabaseContext context, PhoneNumberRepository phones,
            TwilioExistingEnrollmentPlanner planner, JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.context = context;
        this.phones = phones;
        this.planner = planner;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public enum Code {
        SYSTEM_SCOPE_REQUIRED, INVALID_INTENT, NOT_REVIEWABLE,
        ROW_CONFLICT, REPLAY_CONFLICT, STORE_CONFLICT, APPLIED, ALREADY_APPLIED
    }

    /** No provider SID, tenant identifier or raw exception is returned. */
    public record Decision(Code code, TwilioExistingEnrollmentPlanner.Code reviewCode) {
        @Override public String toString() { return "ExistingEnrollment[" + code + "]"; }
    }

    private record Claim(UUID businessId, UUID phoneId,
                         String destination, String ownerAccountSid, String phoneSid,
                         UUID requester, UUID approver) {
        @Override public String toString() { return "ExistingEnrollmentClaim[REDACTED]"; }
    }

    /**
     * Input is INTERNAL server-side intent, not trustworthy authentication.
     * No SQL write without fresh carrier attestation and a still-identical row.
     * Same change ID is idempotent only for the exact same human/tenant/phone.
     */
    public Decision reconcile(TwilioExistingEnrollmentPlanner.Intent intent) {
        if (context.currentOrDenied().mode() != TenantDatabaseContext.Mode.SYSTEM) {
            return new Decision(Code.SYSTEM_SCOPE_REQUIRED, null);
        }
        if (!admissible(intent)) return new Decision(Code.INVALID_INTENT, null);
        try {
            Claim prior = claim(intent.changeId());
            if (prior != null) return replay(prior, intent);

            PhoneNumber original = phones.findById(intent.persistedPhoneId()).orElse(null);
            TwilioExistingEnrollmentPlanner.Preview preview = planner.preview(intent, original);
            if (!preview.reviewable()) return new Decision(Code.NOT_REVIEWABLE, preview.code());

            return transaction.execute(status -> {
                Claim raced = claim(intent.changeId());
                if (raced != null) return replay(raced, intent);

                var candidate = preview.candidate();
                int changed = jdbc.update("""
                        UPDATE phone_number
                           SET twilio_binding_source = 'VERIFIED_EXISTING',
                               twilio_account_sid = ?, external_id = ?,
                               voice_verified_at = ?, updated_at = CURRENT_TIMESTAMP
                         WHERE id = ? AND business_id = ? AND phone_number = ?
                           AND provider = ? AND active = ?
                           AND twilio_binding_source IS NULL
                           AND voice_verified_at IS NULL
                           AND external_id IS NOT DISTINCT FROM ?
                           AND twilio_account_sid IS NOT DISTINCT FROM ?
                           AND updated_at = ?
                        """,
                        candidate.ownerAccountSid(), candidate.verifiedPhoneSid(),
                        Timestamp.from(Instant.now()), intent.persistedPhoneId(),
                        intent.businessId(), original.getPhoneNumber(), original.getProvider(),
                        original.isActive(), original.getExternalId(), original.getTwilioAccountSid(),
                        Timestamp.from(original.getUpdatedAt()));
                if (changed != 1) {
                    Claim completed = claim(intent.changeId());
                    return completed == null
                            ? new Decision(Code.ROW_CONFLICT, null)
                            : replay(completed, intent);
                }
                jdbc.update("""
                        INSERT INTO twilio_existing_enrollment_claim (
                            change_id, business_id, phone_number_id, destination_e164,
                            owner_account_sid, phone_sid, requester_id, approver_id, enrolled_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                        candidate.changeId(), candidate.businessId(), candidate.phoneId(),
                        candidate.destinationE164(), candidate.ownerAccountSid(),
                        candidate.verifiedPhoneSid(), candidate.requesterId(), candidate.approverId());
                return new Decision(Code.APPLIED, null);
            });
        } catch (DataAccessException ex) {
            // Never reflect SQL/driver messages or carrier IDs into a caller.
            return new Decision(Code.STORE_CONFLICT, null);
        }
    }

    private static boolean admissible(TwilioExistingEnrollmentPlanner.Intent intent) {
        return intent != null && intent.changeId() != null
                && intent.businessId() != null && intent.persistedPhoneId() != null
                && intent.requester() != null && intent.approver() != null
                && intent.requester().subjectId() != null && intent.approver().subjectId() != null
                && intent.requester().authority() == TwilioExistingEnrollmentPlanner.Authority.PLATFORM_OPERATOR
                && intent.approver().authority() == TwilioExistingEnrollmentPlanner.Authority.PLATFORM_APPROVER
                && !intent.requester().subjectId().equals(intent.approver().subjectId());
    }

    private Claim claim(UUID changeId) {
        List<Claim> matches = jdbc.query("""
                SELECT business_id, phone_number_id, destination_e164,
                       owner_account_sid, phone_sid, requester_id, approver_id
                  FROM twilio_existing_enrollment_claim WHERE change_id = ?
                """, (rs, ignored) -> new Claim(
                rs.getObject("business_id", UUID.class),
                rs.getObject("phone_number_id", UUID.class),
                rs.getString("destination_e164"),
                rs.getString("owner_account_sid"),
                rs.getString("phone_sid"),
                rs.getObject("requester_id", UUID.class),
                rs.getObject("approver_id", UUID.class)), changeId);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private Decision replay(Claim prior, TwilioExistingEnrollmentPlanner.Intent intent) {
        if (!prior.businessId().equals(intent.businessId())
                || !prior.phoneId().equals(intent.persistedPhoneId())
                || !prior.destination().equals(intent.destinationE164())
                || !prior.ownerAccountSid().equals(intent.ownerAccountSid())
                || !prior.requester().equals(intent.requester().subjectId())
                || !prior.approver().equals(intent.approver().subjectId())) {
            return new Decision(Code.REPLAY_CONFLICT, null);
        }
        List<Integer> binding = jdbc.query("""
                SELECT 1 FROM phone_number
                 WHERE id = ? AND business_id = ? AND phone_number = ?
                   AND provider = 'TWILIO' AND twilio_binding_source = 'VERIFIED_EXISTING'
                   AND external_id = ? AND twilio_account_sid = ?
                   AND voice_verified_at IS NOT NULL
                """, (rs, ignored) -> rs.getInt(1),
                prior.phoneId(), prior.businessId(), prior.destination(),
                prior.phoneSid(), prior.ownerAccountSid());
        return binding.isEmpty()
                ? new Decision(Code.ROW_CONFLICT, null)
                : new Decision(Code.ALREADY_APPLIED, null);
    }
}
