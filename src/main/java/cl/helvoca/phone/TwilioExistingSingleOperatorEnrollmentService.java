package cl.helvoca.phone;

import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier;
import org.springframework.beans.factory.annotation.Value;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public final class TwilioExistingSingleOperatorEnrollmentService {
    private static final Pattern ACCOUNT = Pattern.compile("AC[0-9a-fA-F]{32}");

    private final JdbcTemplate jdbc;
    private final TwilioExistingEnrollmentAdminService admin;
    private final TwilioExistingNumberOwnershipVerifier carrier;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final String environment;
    private final String pinnedBusiness;
    private final String pinnedNumber;

    public TwilioExistingSingleOperatorEnrollmentService(
            JdbcTemplate jdbc,
            TwilioExistingEnrollmentAdminService admin,
            TwilioExistingNumberOwnershipVerifier carrier,
            PasswordEncoder passwords,
            PlatformTransactionManager manager,
            @Value("${TWILIO_SINGLE_OPERATOR_DEMO_ENROLLMENT_ENABLED:false}") boolean enabled,
            @Value("${RAILWAY_ENVIRONMENT_NAME:}") String environment,
            @Value("${TWILIO_CERTIFICATION_DEMO_BUSINESS_ID:}") String pinnedBusiness,
            @Value("${TWILIO_TEST_FROM:}") String pinnedNumber) {
        this.jdbc = jdbc;
        this.admin = admin;
        this.carrier = carrier;
        this.passwords = passwords;
        this.transaction = new TransactionTemplate(manager);
        this.enabled = enabled;
        this.environment = environment;
        this.pinnedBusiness = pinnedBusiness;
        this.pinnedNumber = pinnedNumber;
    }

    public record Request(UUID businessId, UUID phoneId, String ownerAccountSid) {}
    public record Confirm(String currentPassword, String expectedPhoneNumber) {
        @Override public String toString() { return "SingleOperatorConfirm[REDACTED]"; }
    }
    public record View(UUID changeId, String state) {}
    private record Pending(UUID changeId, UUID businessId, UUID phoneId, String destination,
                           String account, UUID actorId, String state, Instant expiresAt,
                           int failedAttempts) {}

    public View request(Request data) {
        UUID actor = admin.authenticatedPlatformActor();
        requireDemo();
        if (data == null || data.businessId() == null || data.phoneId() == null
                || !data.businessId().toString().equals(pinnedBusiness)
                || data.ownerAccountSid() == null || !ACCOUNT.matcher(data.ownerAccountSid()).matches()) {
            throw conflict("Single-operator request unavailable");
        }
        List<String> numbers = jdbc.query("""
                SELECT phone_number FROM phone_number
                 WHERE id = ? AND business_id = ? AND provider = 'TWILIO'
                   AND active = FALSE AND whatsapp_enabled = FALSE
                   AND twilio_binding_source IS NULL AND voice_verified_at IS NULL
                   AND external_id IS NULL AND twilio_account_sid IS NULL
                """, (rs, index) -> rs.getString(1), data.phoneId(), data.businessId());
        if (numbers.size() != 1 || !pinnedNumber.equals(numbers.get(0))) {
            throw conflict("Single-operator inventory unavailable");
        }
        var proof = carrier.verify(numbers.get(0), data.ownerAccountSid(), null);
        if (proof == null || !proof.verified()
                || !data.ownerAccountSid().equals(proof.evidence().ownerAccountSid())) {
            throw conflict("Carrier ownership not verified");
        }
        return transaction.execute(tx -> {
            List<String> locked = jdbc.query("""
                    SELECT p.phone_number FROM phone_number p
                    JOIN business b ON b.id = p.business_id
                    WHERE p.id = ? AND p.business_id = ? AND p.provider = 'TWILIO'
                      AND p.active = FALSE AND p.whatsapp_enabled = FALSE
                      AND p.twilio_binding_source IS NULL AND p.voice_verified_at IS NULL
                      AND p.external_id IS NULL AND p.twilio_account_sid IS NULL
                      AND b.status = 'ACTIVE' AND b.mode IN ('DEMO','CUSTOMER')
                    FOR UPDATE OF p
                    """, (rs, index) -> rs.getString(1), data.phoneId(), data.businessId());
            if (locked.size() != 1 || !numbers.get(0).equals(locked.get(0))) {
                throw conflict("Inventory changed before request");
            }
            Long outstanding = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM twilio_single_operator_demo_enrollment
                    WHERE phone_number_id = ?
                      AND (state = 'APPLIED'
                       OR (state = 'PENDING' AND expires_at > clock_timestamp()))
                    """, Long.class, data.phoneId());
            if (outstanding == null || outstanding != 0L) {
                throw conflict("Enrollment already pending or applied");
            }
            UUID change = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO twilio_single_operator_demo_enrollment
                    (change_id, business_id, phone_number_id, destination_e164,
                     owner_account_sid, actor_id, expires_at)
                    VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP + INTERVAL '15 minutes')
                    """, change, data.businessId(), data.phoneId(),
                    locked.get(0), data.ownerAccountSid(), actor);
            audit(change, actor, "REQUESTED");
            return new View(change, "PENDING");
        });
    }

    public View confirm(UUID changeId, Confirm confirmation) {
        UUID actor = admin.authenticatedPlatformActor();
        requireDemo();
        requireFreshJwt();
        if (changeId == null || confirmation == null || confirmation.currentPassword() == null
                || confirmation.currentPassword().isBlank()
                || confirmation.expectedPhoneNumber() == null
                || !confirmation.expectedPhoneNumber().equals(pinnedNumber)) {
            throw conflict("Explicit confirmation required");
        }
        Pending pending = transaction.execute(tx -> {
            Pending row = locked(changeId);
            requirePending(row, actor);
            if (!row.destination().equals(confirmation.expectedPhoneNumber())) {
                throw conflict("Confirmed destination differs");
            }
            String hash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ? AND active = TRUE AND business_id IS NULL",
                    String.class, actor);
            if (hash == null || !passwords.matches(confirmation.currentPassword(), hash)) {
                jdbc.update("""
                        UPDATE twilio_single_operator_demo_enrollment
                           SET failed_attempts = failed_attempts + 1
                         WHERE change_id = ? AND state = 'PENDING' AND failed_attempts < 3
                        """, changeId);
                audit(changeId, actor, "DENIED");
                return null;
            }
            return row;
        });
        if (pending == null) {
            throw new AccessDeniedException("Fresh platform authentication required");
        }
        var verified = carrier.verify(pending.destination(), pending.account(), null);
        if (verified == null || !verified.verified()
                || !pending.account().equals(verified.evidence().ownerAccountSid())) {
            throw conflict("Fresh carrier verification failed");
        }

        return transaction.execute(tx -> {
            Pending row = locked(changeId);
            requirePending(row, actor);
            if (!row.equals(pending)) {
                throw conflict("Enrollment changed during carrier verification");
            }
            int changed = jdbc.update("""
                    UPDATE phone_number
                       SET twilio_binding_source = 'VERIFIED_EXISTING',
                           twilio_account_sid = ?, external_id = ?,
                           voice_verified_at = clock_timestamp(),
                           active = TRUE, updated_at = clock_timestamp()
                     WHERE id = ? AND business_id = ? AND phone_number = ?
                       AND provider = 'TWILIO' AND active = FALSE
                       AND whatsapp_enabled = FALSE
                       AND twilio_binding_source IS NULL AND voice_verified_at IS NULL
                       AND external_id IS NULL AND twilio_account_sid IS NULL
                       AND EXISTS (
                           SELECT 1 FROM business
                            WHERE id = ? AND status = 'ACTIVE'
                              AND mode IN ('DEMO','CUSTOMER'))
                    """, pending.account(), verified.evidence().phoneSid(),
                    row.phoneId(), row.businessId(), row.destination(), row.businessId());
            if (changed != 1) throw conflict("Carrier binding conflict");
            int recorded = jdbc.update("""
                    UPDATE twilio_single_operator_demo_enrollment
                       SET state = 'APPLIED', phone_sid = ?,
                           carrier_verified_at = clock_timestamp(),
                           confirmed_at = clock_timestamp()
                     WHERE change_id = ? AND state = 'PENDING' AND failed_attempts < 3
                    """, verified.evidence().phoneSid(), changeId);
            if (recorded != 1) throw conflict("Enrollment no longer confirmable");
            audit(changeId, actor, "CONFIRMED");
            return new View(changeId, "APPLIED");
        });
    }

    public View status(UUID changeId) {
        UUID actor = admin.authenticatedPlatformActor();
        requireDemo();
        if (changeId == null) throw conflict("Missing change ID");
        List<String> results = jdbc.query("""
                SELECT state FROM twilio_single_operator_demo_enrollment
                 WHERE change_id = ? AND actor_id = ? AND business_id::text = ?
                """, (rs, index) -> rs.getString(1), changeId, actor, pinnedBusiness);
        if (results.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found");
        return new View(changeId, results.get(0));
    }

    private Pending locked(UUID changeId) {
        List<Pending> matches = jdbc.query("""
                SELECT change_id,business_id,phone_number_id,destination_e164,
                       owner_account_sid,actor_id,state,expires_at,failed_attempts
                  FROM twilio_single_operator_demo_enrollment
                 WHERE change_id = ? FOR UPDATE
                """, (rs, index) -> row(rs), changeId);
        if (matches.size() != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found");
        return matches.get(0);
    }

    private static Pending row(ResultSet rs) throws SQLException {
        return new Pending(rs.getObject("change_id", UUID.class),
                rs.getObject("business_id", UUID.class),
                rs.getObject("phone_number_id", UUID.class),
                rs.getString("destination_e164"), rs.getString("owner_account_sid"),
                rs.getObject("actor_id", UUID.class), rs.getString("state"),
                rs.getTimestamp("expires_at").toInstant(), rs.getInt("failed_attempts"));
    }

    private void requirePending(Pending row, UUID actor) {
        if (!row.actorId().equals(actor) || !row.businessId().toString().equals(pinnedBusiness)
                || !pinnedNumber.equals(row.destination())) {
            throw new AccessDeniedException("Enrollment actor or tenant mismatch");
        }
        if (!"PENDING".equals(row.state()) || row.failedAttempts() >= 3
                || !Instant.now().isBefore(row.expiresAt())) {
            throw conflict("Enrollment expired or no longer pending");
        }
    }

    private void requireDemo() {
        if (!enabled || !"demo-isolated".equals(environment)
                || pinnedBusiness == null || !pinnedBusiness.matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                || pinnedNumber == null || !pinnedNumber.matches("\\+[1-9][0-9]{7,14}")) {
            throw conflict("Single-operator demo enrollment is disabled");
        }
    }

    private static void requireFreshJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)
                || jwt.getIssuedAt() == null
                || jwt.getIssuedAt().isBefore(Instant.now().minusSeconds(300))
                || jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30))) {
            throw new AccessDeniedException("Recent platform login required");
        }
    }

    private void audit(UUID change, UUID actor, String action) {
        jdbc.update("""
                INSERT INTO twilio_single_operator_demo_audit(id,change_id,actor_id,action)
                VALUES (?,?,?,?)
                """, UUID.randomUUID(), change, actor, action);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
