package cl.helvoca.phone;

import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Phase 6: genuine JWT principal + currently active database platform role.
 * Decisions are persisted under forced SYSTEM-only PostgreSQL RLS. No tenant
 * input can supply the approving human or carrier PN SID.
 */
@Service
public final class TwilioExistingEnrollmentAdminService {
    private static final Pattern ACCOUNT = Pattern.compile("AC[0-9a-fA-F]{32}");

    private final JdbcTemplate jdbc;
    private final TenantDatabaseContext context;
    private final TwilioExistingEnrollmentReconciler reconciler;
    private final TransactionTemplate transaction;

    public TwilioExistingEnrollmentAdminService(
            JdbcTemplate jdbc, TenantDatabaseContext context,
            TwilioExistingEnrollmentReconciler reconciler,
            PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.context = context;
        this.reconciler = reconciler;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** Operator-supplied expected account is a routing hint, never Twilio proof. */
    public record Request(UUID businessId, UUID phoneId, String ownerAccountSid) {}
    public record View(UUID changeId, String state) {}

    private record RequestRow(UUID changeId, UUID businessId, UUID phoneId,
                              String destination, String account,
                              UUID requester, UUID approver, String state,
                              Instant expiresAt) {}

    public View request(Request data) {
        UUID actor = actor();
        if (data == null || data.businessId() == null || data.phoneId() == null
                || data.ownerAccountSid() == null
                || !ACCOUNT.matcher(data.ownerAccountSid()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid enrollment request");
        }
        return transaction.execute(status -> {
            List<String> numbers = jdbc.query("""
                    SELECT phone_number FROM phone_number
                     WHERE id = ? AND business_id = ? AND provider = 'TWILIO'
                       AND twilio_binding_source IS NULL AND voice_verified_at IS NULL
                     FOR UPDATE
                    """, (rs, ignored) -> rs.getString(1), data.phoneId(), data.businessId());
            if (numbers.isEmpty()) throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Phone is not eligible");
            long pending = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM twilio_existing_enrollment_request
                     WHERE phone_number_id = ? AND state IN ('PENDING','APPROVED')
                       AND expires_at > clock_timestamp()
                    """, Long.class, data.phoneId());
            if (pending != 0) throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Enrollment already pending");
            UUID change = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO twilio_existing_enrollment_request(
                        change_id,business_id,phone_number_id,destination_e164,
                        owner_account_sid,requester_id,expires_at
                    ) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP + INTERVAL '15 minutes')
                    """, change, data.businessId(), data.phoneId(),
                    numbers.get(0), data.ownerAccountSid(), actor);
            audit(change, actor, "REQUESTED", "PENDING");
            return new View(change, "PENDING");
        });
    }

    public View approve(UUID changeId) {
        UUID actor = actor();
        return transaction.execute(status -> {
            RequestRow row = locked(changeId);
            if (!"PENDING".equals(row.state())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Request not pending");
            }
            if (!Instant.now().isBefore(row.expiresAt())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Request expired");
            }
            if (actor.equals(row.requester())) {
                throw new AccessDeniedException("Independent platform approver required");
            }
            jdbc.update("""
                    UPDATE twilio_existing_enrollment_request
                       SET approver_id = ?, approved_at = clock_timestamp(), state = 'APPROVED'
                     WHERE change_id = ? AND state = 'PENDING'
                    """, actor, changeId);
            audit(changeId, actor, "APPROVED", "APPROVED");
            return new View(changeId, "APPROVED");
        });
    }

    public View execute(UUID changeId) {
        UUID actor = actor();
        return transaction.execute(status -> {
            RequestRow row = locked(changeId);
            if (!"APPROVED".equals(row.state())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Request not approved");
            }
            if (!actor.equals(row.approver())) {
                throw new AccessDeniedException("Only the approving platform administrator may execute");
            }
            if (!Instant.now().isBefore(row.expiresAt())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Request expired");
            }
            if (!activePlatformAdmin(row.requester())) {
                throw new AccessDeniedException("Requesting platform administrator is no longer active");
            }
            var intent = new TwilioExistingEnrollmentPlanner.Intent(
                    row.businessId(), row.phoneId(), row.destination(), row.account(), row.changeId(),
                    new TwilioExistingEnrollmentPlanner.Actor(row.requester(),
                            TwilioExistingEnrollmentPlanner.Authority.PLATFORM_OPERATOR),
                    new TwilioExistingEnrollmentPlanner.Actor(row.approver(),
                            TwilioExistingEnrollmentPlanner.Authority.PLATFORM_APPROVER));
            var result = reconciler.reconcile(intent);
            if (result.code() != TwilioExistingEnrollmentReconciler.Code.APPLIED
                    && result.code() != TwilioExistingEnrollmentReconciler.Code.ALREADY_APPLIED) {
                audit(changeId, actor, "EXECUTION_BLOCKED", result.code().name());
                return new View(changeId, "BLOCKED");
            }
            jdbc.update("""
                    UPDATE twilio_existing_enrollment_request
                       SET state = 'APPLIED', executed_at = clock_timestamp()
                     WHERE change_id = ? AND state = 'APPROVED'
                    """, changeId);
            audit(changeId, actor, "EXECUTED", "APPLIED");
            return new View(changeId, "APPLIED");
        });
    }

    public View status(UUID changeId) {
        actor();
        RequestRow row = lookup(changeId, false);
        return new View(row.changeId(), row.state());
    }

    private RequestRow locked(UUID id) { return lookup(id, true); }

    private RequestRow lookup(UUID id, boolean lock) {
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing change ID");
        List<RequestRow> rows = jdbc.query("""
                SELECT change_id,business_id,phone_number_id,destination_e164,
                       owner_account_sid,requester_id,approver_id,state,expires_at
                FROM twilio_existing_enrollment_request WHERE change_id = ?
                """ + (lock ? " FOR UPDATE" : ""),
                (rs, ignored) -> fromRow(rs), id);
        if (rows.isEmpty()) throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Enrollment not found");
        return rows.get(0);
    }

    private static RequestRow fromRow(ResultSet rs) throws java.sql.SQLException {
        return new RequestRow(
                rs.getObject("change_id", UUID.class),
                rs.getObject("business_id", UUID.class),
                rs.getObject("phone_number_id", UUID.class),
                rs.getString("destination_e164"),
                rs.getString("owner_account_sid"),
                rs.getObject("requester_id", UUID.class),
                rs.getObject("approver_id", UUID.class),
                rs.getString("state"),
                rs.getTimestamp("expires_at").toInstant());
    }

    private void audit(UUID changeId, UUID actor, String action, String outcome) {
        jdbc.update("""
                INSERT INTO twilio_existing_enrollment_audit
                (id,change_id,actor_id,action,outcome)
                VALUES (?,?,?,?,?)
                """, UUID.randomUUID(), changeId, actor, action, outcome);
    }

    private UUID actor() {
        if (context.currentOrDenied().mode() != TenantDatabaseContext.Mode.SYSTEM) {
            throw new AccessDeniedException("Platform system database scope required");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)
                || !auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()))
                || jwt.getClaimAsStringList("roles") == null
                || !jwt.getClaimAsStringList("roles").contains("PLATFORM_ADMIN")) {
            throw new AccessDeniedException("Signed platform JWT required");
        }
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new AccessDeniedException("Invalid platform subject");
        }
        if (!activePlatformAdmin(id)
                || !Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM app_user
                                   WHERE id = ? AND LOWER(email) = LOWER(?))
                    """, Boolean.class, id, jwt.getClaimAsString("email")))) {
            throw new AccessDeniedException("Platform administrator is not active");
        }
        return id;
    }

    private boolean activePlatformAdmin(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM app_user u
                      JOIN user_role ur ON ur.user_id = u.id
                      JOIN role r ON r.id = ur.role_id
                    WHERE u.id = ? AND u.active AND u.business_id IS NULL
                      AND r.code = 'PLATFORM_ADMIN')
                """, Boolean.class, id));
    }
}
