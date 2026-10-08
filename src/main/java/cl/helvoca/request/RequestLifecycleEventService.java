package cl.helvoca.request;

import cl.helvoca.security.TenantProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Appends the real actor and state change, independent of the operation
 * event trigger which uses the original source channel as actor hint.
 */
@Service
public class RequestLifecycleEventService {
    public record Transition(UUID id, RequestStatus previousStatus,
                             RequestStatus status, String actorType,
                             String reasonCode, UUID evidenceEventId) { }

    private final JdbcTemplate jdbc;
    private final TenantProvider tenants;

    public RequestLifecycleEventService(JdbcTemplate jdbc, TenantProvider tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    @Transactional
    public void recordBusinessUser(UUID businessId, UUID requestId, UUID operationId,
                                   RequestStatus before, RequestStatus after) {
        // Never accept tenant identity from an HTTP payload.
        if (!businessId.equals(tenants.requireBusinessId())) {
            throw new IllegalArgumentException("Request tenant does not match authenticated user");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null ? null : authentication.getName();
        if (actor != null && actor.length() > 160) actor = actor.substring(0, 160);
        jdbc.update("""
                INSERT INTO business_request_transition_event (
                    business_id, request_id, operation_id, previous_status,
                    status, actor_type, actor_reference, reason_code)
                VALUES (?, ?, ?, ?, ?, 'BUSINESS_USER', ?, 'MANUAL_STATUS_UPDATE')
                """,
                businessId, requestId, operationId, before.name(), after.name(), actor);
    }

    @Transactional(readOnly = true)
    public List<Transition> history(UUID requestId) {
        UUID businessId = tenants.requireBusinessId();
        return jdbc.query("""
                SELECT id, previous_status, status, actor_type, reason_code, evidence_event_id
                  FROM business_request_transition_event
                 WHERE business_id = ? AND request_id = ?
                 ORDER BY created_at ASC, id ASC
                """, (rs, row) -> new Transition(
                rs.getObject("id", UUID.class),
                RequestStatus.valueOf(rs.getString("previous_status")),
                RequestStatus.valueOf(rs.getString("status")),
                rs.getString("actor_type"),
                rs.getString("reason_code"),
                rs.getObject("evidence_event_id", UUID.class)), businessId, requestId);
    }
}
