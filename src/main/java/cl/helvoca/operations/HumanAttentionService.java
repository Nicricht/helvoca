package cl.helvoca.operations;

import cl.helvoca.security.TenantProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Unified, tenant-scoped, read-only operational attention query.
 * One actionable item per request/active handoff pair: the handoff wins.
 * No AI or provider action, queue polling or state mutation.
 */
@Service
public class HumanAttentionService {
    public enum Kind { REQUEST, HANDOFF }

    public record AttentionItem(
            Kind kind,
            UUID id,
            UUID operationId,
            String title,
            String priority,
            String status,
            Instant createdAt
    ) { }

    private final JdbcTemplate jdbc;
    private final TenantProvider tenant;

    public HumanAttentionService(JdbcTemplate jdbc, TenantProvider tenant) {
        this.jdbc = jdbc;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public List<AttentionItem> pending() {
        UUID businessId = tenant.requireBusinessId();
        return jdbc.query("""
                SELECT kind, id, operation_id, title, priority, status, created_at
                  FROM (
                        SELECT 'HANDOFF' AS kind,
                               h.id, h.operation_id,
                               COALESCE(h.safe_summary, 'Requiere atención humana') AS title,
                               h.priority, h.status, h.created_at
                          FROM human_handoff h
                         WHERE h.business_id = ?
                           AND h.status IN ('OPEN','ACKNOWLEDGED','ASSIGNED')
                        UNION ALL
                        SELECT 'REQUEST' AS kind,
                               r.id, r.operation_id,
                               r.title, r.priority, r.status, r.created_at
                          FROM business_request r
                         WHERE r.business_id = ?
                           AND r.status IN ('OPEN','IN_PROGRESS')
                           AND NOT EXISTS (
                               SELECT 1
                                 FROM human_handoff h
                                WHERE h.business_id = r.business_id
                                  AND h.operation_id = r.operation_id
                                  AND h.status IN ('OPEN','ACKNOWLEDGED','ASSIGNED'))
                       ) attention
                 ORDER BY created_at DESC, id DESC
                 LIMIT 100
                """, (rs, row) -> {
            Timestamp timestamp = rs.getTimestamp("created_at");
            return new AttentionItem(
                    Kind.valueOf(rs.getString("kind")),
                    rs.getObject("id", UUID.class),
                    rs.getObject("operation_id", UUID.class),
                    rs.getString("title"),
                    rs.getString("priority"),
                    rs.getString("status"),
                    timestamp == null ? null : timestamp.toInstant());
        }, businessId, businessId);
    }
}
