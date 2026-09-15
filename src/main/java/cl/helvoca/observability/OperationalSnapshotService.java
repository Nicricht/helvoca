package cl.helvoca.observability;

import cl.helvoca.jobs.PersistentJobStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant-scoped operational summary containing counts only.
 *
 * No message content, job payload, phone number, customer name or provider
 * credential is exposed by this service.
 */
@Service
public class OperationalSnapshotService {
    private final JdbcTemplate jdbc;
    private final PersistentJobStore jobs;

    public OperationalSnapshotService(JdbcTemplate jdbc, PersistentJobStore jobs) {
        this.jdbc = jdbc;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID businessId) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        PersistentJobStore.JobQueueSummary queue = jobs.summary(businessId);
        return new Snapshot(
                Instant.now(),
                counts("business_operation", businessId),
                queue.byStatus(),
                counts("outbound_message", businessId),
                counts("booking_calendar_event", businessId),
                queue.oldestActiveCreatedAt());
    }

    private Map<String, Long> counts(String table, UUID businessId) {
        String sql = switch (table) {
            case "business_operation" -> "SELECT status, COUNT(*) AS count FROM business_operation WHERE business_id = ? GROUP BY status";
            case "outbound_message" -> "SELECT status, COUNT(*) AS count FROM outbound_message WHERE business_id = ? GROUP BY status";
            case "booking_calendar_event" -> "SELECT status, COUNT(*) AS count FROM booking_calendar_event WHERE business_id = ? GROUP BY status";
            default -> throw new IllegalArgumentException("Unsupported observability table");
        };
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, businessId)) {
            Object rawStatus = row.get("status");
            Object rawCount = row.get("count");
            if (rawStatus != null && rawCount instanceof Number number) {
                result.put(rawStatus.toString(), number.longValue());
            }
        }
        return Map.copyOf(result);
    }

    public record Snapshot(Instant generatedAt,
                           Map<String, Long> operationsByStatus,
                           Map<String, Long> jobsByStatus,
                           Map<String, Long> outboundMessagesByStatus,
                           Map<String, Long> calendarEventsByStatus,
                           Instant oldestActiveJobCreatedAt) {}
}
