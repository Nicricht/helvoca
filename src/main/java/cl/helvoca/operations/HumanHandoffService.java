package cl.helvoca.operations;

import cl.helvoca.security.TenantProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class HumanHandoffService {
    public enum Status { OPEN, ACKNOWLEDGED, ASSIGNED, RESOLVED, CANCELLED }

    public record Creation(UUID handoffId, boolean created, Status status) { }

    public record HandoffView(
            UUID id,
            long sequenceNo,
            UUID sourceReferenceId,
            UUID operationId,
            BusinessOperation.Type operationType,
            String toolName,
            String reasonCode,
            String failureClass,
            String fallbackAction,
            int retryCount,
            String priority,
            Status status,
            String safeSummary,
            String assignedTo,
            Instant createdAt,
            Instant acknowledgedAt,
            Instant assignedAt,
            Instant resolvedAt,
            Instant cancelledAt,
            Instant updatedAt) { }

    public record EventView(
            UUID id,
            long sequenceNo,
            UUID handoffId,
            String eventType,
            Status previousStatus,
            Status status,
            String actorType,
            String actorReference,
            String payload,
            Instant createdAt) { }

    private final JdbcTemplate jdbc;
    private final TenantProvider tenantProvider;

    public HumanHandoffService(JdbcTemplate jdbc, TenantProvider tenantProvider) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Creation createForUnresolvable(UUID businessId,
                                          BusinessOperation.Type operationType,
                                          UUID sourceReferenceId,
                                          UUID operationId,
                                          String toolName,
                                          String reasonCode,
                                          int retryCount) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        if (operationType == null) throw new IllegalArgumentException("operationType is required");
        String safeTool = sanitize(toolName, 80, "unknown_tool");
        String safeReason = sanitize(reasonCode, 80, "UNRESOLVABLE_OPERATION_FAILURE");
        int safeRetryCount = Math.max(0, Math.min(retryCount, 5));
        String priority = operationType == BusinessOperation.Type.PAYMENT ? "HIGH" : "NORMAL";
        String summary = sanitize("La automatización no pudo resolver de forma segura la operación "
                + operationType.name() + ". Motivo: " + safeReason + ".", 500, null);

        UUID handoffId = UUID.randomUUID();
        int inserted;
        try {
            inserted = jdbc.update("""
                    INSERT INTO human_handoff(
                        id, business_id, source_reference_id, operation_id, operation_type,
                        tool_name, reason_code, failure_class, fallback_action, retry_count,
                        priority, status, safe_summary)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'UNRESOLVABLE', 'HUMAN_HANDOFF', ?, ?, 'OPEN', ?)
                    ON CONFLICT DO NOTHING
                    """, handoffId, businessId, sourceReferenceId, operationId, operationType.name(),
                    safeTool, safeReason, safeRetryCount, priority, summary);
        } catch (DataIntegrityViolationException e) {
            inserted = 0;
        }

        if (inserted == 1) {
            appendEvent(handoffId, businessId, "CREATED", null, Status.OPEN,
                    "AUTOMATION", null, "{}");
            return new Creation(handoffId, true, Status.OPEN);
        }

        return jdbc.query("""
                        SELECT id, status
                        FROM human_handoff
                        WHERE business_id = ?
                          AND COALESCE(source_reference_id, '00000000-0000-0000-0000-000000000000'::uuid)
                              = COALESCE(?::uuid, '00000000-0000-0000-0000-000000000000'::uuid)
                          AND COALESCE(operation_id, '00000000-0000-0000-0000-000000000000'::uuid)
                              = COALESCE(?::uuid, '00000000-0000-0000-0000-000000000000'::uuid)
                          AND operation_type = ?
                          AND reason_code = ?
                          AND status IN ('OPEN','ACKNOWLEDGED','ASSIGNED')
                        ORDER BY sequence_no DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> new Creation(rs.getObject("id", UUID.class), false,
                        Status.valueOf(rs.getString("status"))),
                businessId, sourceReferenceId, operationId, operationType.name(), safeReason)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unable to persist or recover human handoff"));
    }

    @Transactional(readOnly = true)
    public List<HandoffView> recent(String rawStatus) {
        UUID businessId = tenantProvider.requireBusinessId();
        if (rawStatus == null || rawStatus.isBlank()) {
            return jdbc.query("""
                    SELECT * FROM human_handoff
                    WHERE business_id = ?
                    ORDER BY sequence_no DESC
                    LIMIT 100
                    """, this::mapHandoff, businessId);
        }
        Status status = Status.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        return jdbc.query("""
                SELECT * FROM human_handoff
                WHERE business_id = ? AND status = ?
                ORDER BY sequence_no DESC
                LIMIT 100
                """, this::mapHandoff, businessId, status.name());
    }

    @Transactional(readOnly = true)
    public List<EventView> history(UUID handoffId) {
        UUID businessId = tenantProvider.requireBusinessId();
        requireOwnedHandoff(businessId, handoffId);
        return jdbc.query("""
                SELECT * FROM human_handoff_event
                WHERE business_id = ? AND handoff_id = ?
                ORDER BY sequence_no ASC
                """, this::mapEvent, businessId, handoffId);
    }

    @Transactional
    public HandoffView acknowledge(UUID handoffId, String actorReference) {
        return transition(handoffId, actorReference, "ACKNOWLEDGED", Status.ACKNOWLEDGED,
                List.of(Status.OPEN), null);
    }

    @Transactional
    public HandoffView assign(UUID handoffId, String assignee, String actorReference) {
        String safeAssignee = sanitize(assignee, 160, null);
        if (safeAssignee == null || safeAssignee.isBlank()) {
            throw new IllegalArgumentException("assignee is required");
        }
        return transition(handoffId, actorReference, "ASSIGNED", Status.ASSIGNED,
                List.of(Status.OPEN, Status.ACKNOWLEDGED, Status.ASSIGNED), safeAssignee);
    }

    @Transactional
    public HandoffView resolve(UUID handoffId, String actorReference) {
        return transition(handoffId, actorReference, "RESOLVED", Status.RESOLVED,
                List.of(Status.OPEN, Status.ACKNOWLEDGED, Status.ASSIGNED), null);
    }

    @Transactional
    public HandoffView cancel(UUID handoffId, String actorReference) {
        return transition(handoffId, actorReference, "CANCELLED", Status.CANCELLED,
                List.of(Status.OPEN, Status.ACKNOWLEDGED, Status.ASSIGNED), null);
    }

    private HandoffView transition(UUID handoffId,
                                   String actorReference,
                                   String eventType,
                                   Status target,
                                   List<Status> allowed,
                                   String assignee) {
        UUID businessId = tenantProvider.requireBusinessId();
        HandoffView current = requireOwnedHandoff(businessId, handoffId);
        if (current.status() == target && target != Status.RESOLVED && target != Status.CANCELLED) {
            return current;
        }
        if (!allowed.contains(current.status())) {
            throw new IllegalStateException("Invalid human handoff transition from "
                    + current.status() + " to " + target);
        }

        Instant now = Instant.now();
        int updated;
        if (target == Status.ASSIGNED) {
            updated = jdbc.update("""
                    UPDATE human_handoff
                    SET status = 'ASSIGNED', assigned_to = ?, assigned_at = COALESCE(assigned_at, ?), updated_at = ?
                    WHERE id = ? AND business_id = ? AND status = ?
                    """, assignee, now, now, handoffId, businessId, current.status().name());
        } else if (target == Status.ACKNOWLEDGED) {
            updated = jdbc.update("""
                    UPDATE human_handoff
                    SET status = 'ACKNOWLEDGED', acknowledged_at = COALESCE(acknowledged_at, ?), updated_at = ?
                    WHERE id = ? AND business_id = ? AND status = ?
                    """, now, now, handoffId, businessId, current.status().name());
        } else if (target == Status.RESOLVED) {
            updated = jdbc.update("""
                    UPDATE human_handoff
                    SET status = 'RESOLVED', resolved_at = ?, updated_at = ?
                    WHERE id = ? AND business_id = ? AND status = ?
                    """, now, now, handoffId, businessId, current.status().name());
        } else {
            updated = jdbc.update("""
                    UPDATE human_handoff
                    SET status = 'CANCELLED', cancelled_at = ?, updated_at = ?
                    WHERE id = ? AND business_id = ? AND status = ?
                    """, now, now, handoffId, businessId, current.status().name());
        }
        if (updated != 1) throw new IllegalStateException("Human handoff changed concurrently");

        appendEvent(handoffId, businessId, eventType, current.status(), target,
                "BUSINESS_USER", sanitize(actorReference, 160, null), "{}");
        return requireOwnedHandoff(businessId, handoffId);
    }

    private HandoffView requireOwnedHandoff(UUID businessId, UUID handoffId) {
        if (handoffId == null) throw new IllegalArgumentException("handoffId is required");
        return jdbc.query("SELECT * FROM human_handoff WHERE business_id = ? AND id = ?",
                        this::mapHandoff, businessId, handoffId)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Human handoff not found"));
    }

    private void appendEvent(UUID handoffId,
                             UUID businessId,
                             String eventType,
                             Status previousStatus,
                             Status status,
                             String actorType,
                             String actorReference,
                             String payload) {
        jdbc.update("""
                INSERT INTO human_handoff_event(
                    handoff_id, business_id, event_type, previous_status, status,
                    actor_type, actor_reference, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, handoffId, businessId, eventType,
                previousStatus == null ? null : previousStatus.name(), status.name(),
                actorType, actorReference, payload == null ? "{}" : payload);
    }

    private HandoffView mapHandoff(ResultSet rs, int rowNum) throws SQLException {
        return new HandoffView(
                rs.getObject("id", UUID.class),
                rs.getLong("sequence_no"),
                rs.getObject("source_reference_id", UUID.class),
                rs.getObject("operation_id", UUID.class),
                BusinessOperation.Type.valueOf(rs.getString("operation_type")),
                rs.getString("tool_name"),
                rs.getString("reason_code"),
                rs.getString("failure_class"),
                rs.getString("fallback_action"),
                rs.getInt("retry_count"),
                rs.getString("priority"),
                Status.valueOf(rs.getString("status")),
                rs.getString("safe_summary"),
                rs.getString("assigned_to"),
                instant(rs, "created_at"),
                instant(rs, "acknowledged_at"),
                instant(rs, "assigned_at"),
                instant(rs, "resolved_at"),
                instant(rs, "cancelled_at"),
                instant(rs, "updated_at"));
    }

    private EventView mapEvent(ResultSet rs, int rowNum) throws SQLException {
        String previous = rs.getString("previous_status");
        return new EventView(
                rs.getObject("id", UUID.class),
                rs.getLong("sequence_no"),
                rs.getObject("handoff_id", UUID.class),
                rs.getString("event_type"),
                previous == null ? null : Status.valueOf(previous),
                Status.valueOf(rs.getString("status")),
                rs.getString("actor_type"),
                rs.getString("actor_reference"),
                rs.getString("payload"),
                instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String sanitize(String value, int maxLength, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String cleaned = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
