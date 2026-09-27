package cl.helvoca.reconciliation;

import org.json.JSONObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReconciliationRepository {
    private static final Duration OUTBOUND_STUCK_AFTER = Duration.ofMinutes(10);
    private static final Duration WEBHOOK_RETRY_AFTER = Duration.ofMinutes(2);

    private final JdbcTemplate jdbc;

    public ReconciliationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ReconciliationAnomaly> detect(UUID businessId, Instant now) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        Instant detectedAt = now == null ? Instant.now() : now;
        List<ReconciliationAnomaly> out = new ArrayList<>();

        detectSucceededOrderMismatch(businessId, detectedAt, out);
        detectSucceededInventoryHold(businessId, detectedAt, out);
        detectTerminalInventoryHold(businessId, detectedAt, out);
        detectStuckOutbound(businessId, detectedAt, out);
        detectOrphanReservations(businessId, detectedAt, out);
        detectFailedWebhooks(businessId, detectedAt, out);
        detectStuckJobs(businessId, detectedAt, out);
        detectJourneyMismatch(businessId, detectedAt, out);

        out.sort((left, right) -> Integer.compare(
                severityRank(left.severity()), severityRank(right.severity())));
        return List.copyOf(out);
    }

    private void detectSucceededOrderMismatch(
            UUID businessId, Instant detectedAt, List<ReconciliationAnomaly> out) {
        jdbc.query("""
                SELECT p.id AS payment_id,
                       p.target_operation_id,
                       o.status AS order_status
                  FROM business_payment p
                  JOIN business_operation o
                    ON o.id = p.target_operation_id
                   AND o.business_id = p.business_id
                 WHERE p.business_id = ?
                   AND p.status = 'SUCCEEDED'
                   AND o.type = 'ORDER'
                   AND o.status <> 'CONFIRMED'
                 ORDER BY p.updated_at ASC
                 LIMIT 100
                """, rs -> out.add(anomaly(
                ReconciliationAnomaly.Type.PAYMENT_SUCCEEDED_ORDER_NOT_CONFIRMED,
                ReconciliationAnomaly.Severity.CRITICAL,
                "PAYMENT",
                uuid(rs.getObject("payment_id")),
                uuid(rs.getObject("target_operation_id")),
                detectedAt,
                false,
                "Revisar el pedido y el pago antes de modificar estado; no se repara automáticamente.",
                Map.of("orderStatus", rs.getString("order_status")))), businessId);
    }

    private void detectSucceededInventoryHold(
            UUID businessId, Instant detectedAt, List<ReconciliationAnomaly> out) {
        jdbc.query("""
                SELECT p.id AS payment_id,
                       p.target_operation_id,
                       COUNT(r.id) AS reservation_count
                  FROM business_payment p
                  JOIN inventory_reservation r
                    ON r.business_id = p.business_id
                   AND r.reference_type = 'ORDER_OPERATION'
                   AND r.reference_id = p.target_operation_id
                   AND r.status = 'ACTIVE'
                 WHERE p.business_id = ?
                   AND p.status = 'SUCCEEDED'
                 GROUP BY p.id, p.target_operation_id
                 ORDER BY MIN(r.created_at) ASC
                 LIMIT 100
                """, rs -> out.add(anomaly(
                ReconciliationAnomaly.Type.PAYMENT_SUCCEEDED_INVENTORY_RESERVED,
                ReconciliationAnomaly.Severity.CRITICAL,
                "PAYMENT",
                uuid(rs.getObject("payment_id")),
                uuid(rs.getObject("target_operation_id")),
                detectedAt,
                true,
                "Consumir idempotentemente la reserva de inventario del pedido pagado.",
                Map.of("activeReservations", String.valueOf(rs.getLong("reservation_count"))))), businessId);
    }

    private void detectTerminalInventoryHold(
            UUID businessId, Instant detectedAt, List<ReconciliationAnomaly> out) {
        jdbc.query("""
                SELECT MIN(p.id::text)::uuid AS payment_id,
                       p.target_operation_id,
                       MIN(p.status) AS payment_status,
                       COUNT(DISTINCT r.id) AS reservation_count
                  FROM business_payment p
                  JOIN inventory_reservation r
                    ON r.business_id = p.business_id
                   AND r.reference_type = 'ORDER_OPERATION'
                   AND r.reference_id = p.target_operation_id
                   AND r.status = 'ACTIVE'
                 WHERE p.business_id = ?
                   AND p.status IN ('FAILED','CANCELLED','EXPIRED')
                   AND NOT EXISTS (
                       SELECT 1
                         FROM business_payment active_payment
                        WHERE active_payment.business_id = p.business_id
                          AND active_payment.target_operation_id = p.target_operation_id
                          AND active_payment.status IN ('SUCCEEDED','PENDING','REQUIRES_ACTION')
                   )
                 GROUP BY p.target_operation_id
                 ORDER BY p.target_operation_id
                 LIMIT 100
                """, rs -> out.add(anomaly(
                ReconciliationAnomaly.Type.PAYMENT_TERMINAL_INVENTORY_RESERVED,
                ReconciliationAnomaly.Severity.HIGH,
                "PAYMENT",
                uuid(rs.getObject("payment_id")),
                uuid(rs.getObject("target_operation_id")),
                detectedAt,
                true,
                "Liberar idempotentemente las reservas del pedido sin pago activo o exitoso.",
                Map.of(
                        "paymentStatus", rs.getString("payment_status"),
                        "activeReservations", String.valueOf(rs.getLong("reservation_count"))))), businessId);
    }

    private void detectStuckOutbound(
            UUID businessId, Instant detectedAt, List<ReconciliationAnomaly> out) {
        Instant cutoff = detectedAt.minus(OUTBOUND_STUCK_AFTER);
        jdbc.query("""
                SELECT id, operation_id, purpose, created_at
                  FROM outbound_message
                 WHERE business_id = ?
                   AND status = 'PREPARED'
                   AND created_at <= ?
                 ORDER BY created_at ASC
                 LIMIT 100
                """, rs -> out.add(anomaly(
                ReconciliationAnomaly.Type.OUTBOUND_STUCK_PREPARED,
                ReconciliationAnomaly.Severity.HIGH,
                "OUTBOUND_MESSAGE",
                uuid(rs.getObject("id")),
                uuid(rs.getObject("operation_id")),
                detectedAt,
                false,
                "Revisar delivery switch/proveedor y encolar solo con autorización de tráfico real.",
                Map.of(
                        "purpose", safe(rs.getString("purpose")),
                        "createdAt", instantText(rs.getTimestamp("created_at"))))),
                businessId, Timestamp.from(cutoff));
    }

    private void detectOrphanReservations(
            UUID businessId, Instant detectedAt, List<ReconciliationAnomaly> out) {
        jdbc.query("""
                SELECT r.reference_id,
                       MIN(r.id::text)::uuid AS reservation_id,
                       COUNT(*) AS reservation_count
                  FROM inventory_reservation r
                 WHERE r.business_id = ?
                   AND r.status = 'ACTIVE'
                   AND r.reference_type = 'ORDER_OPERATION'
                   AND r.reference_id IS NOT NULL
                   AND NOT EXISTS (
                       SELECT 1
                         FROM business_operation o
                        WHERE o.business_id = r.business_id
                          AND o.id = r.reference_id
                   )
                 GROUP BY r.reference_id
                 ORDER BY r.reference_id
                 LIMIT 100
                """, rs -> out.add(anomaly(
                ReconciliationAnomaly.Type.ORPHAN_RESERVATION,
                ReconciliationAnomaly.Severity.HIGH,
                "INVENTORY_RESERVATION",
                uuid(rs.getObject("reservation_id")),
                uuid(rs.getObject("reference_id")),
                detectedAt,
                true,
                "Liberar la reserva huérfana porque su operación ORDER ya no existe.",
                Map.of("activeReservations", String.valueOf(rs.getLong("reservation_count"))))), businessId);
    }

    private void detectFailedWebhooks(
            UUID businessId, Instant detectedAt, List<ReconciliationAnomaly> out) {
        Instant cutoff = detectedAt.minus(WEBHOOK_RETRY_AFTER);
        jdbc.query("""
                SELECT id, provider, event_id, external_id, processed_at
                  FROM payment_webhook_event
                 WHERE business_id = ?
                   AND status = 'FAILED'
                   AND COALESCE(processed_at, received_at) <= ?
                 ORDER BY COALESCE(processed_at, received_at) ASC
                 LIMIT 100
                """, rs -> out.add(anomaly(
                ReconciliationAnomaly.Type.RECOVERABLE_WEBHOOK_FAILED,
                ReconciliationAnomaly.Severity.HIGH,
                "PAYMENT_WEBHOOK",
                uuid(rs.getObject("id")),
                null,
                detectedAt,
                false,
                "Reconsultar al proveedor mediante un flujo autorizado; el payload original no se persiste.",
                Map.of(
                        "provider", safe(rs.getString("provider")),
                        "eventId", safe(rs.getString("event_id")),
                        "externalId", safe(rs.getString("external_id"))))),
                businessId, Timestamp.from(cutoff));
    }

    private void detectStuckJobs(
            UUID businessId, Instant detectedAt, List<ReconciliationAnomaly> out) {
        jdbc.query("""
                SELECT id, operation_id, job_type, attempt_count, max_attempts, lease_expires_at
                  FROM persistent_job
                 WHERE business_id = ?
                   AND status = 'RUNNING'
                   AND lease_expires_at <= ?
                 ORDER BY lease_expires_at ASC
                 LIMIT 100
                """, rs -> out.add(anomaly(
                ReconciliationAnomaly.Type.JOB_STUCK,
                ReconciliationAnomaly.Severity.MEDIUM,
                "PERSISTENT_JOB",
                uuid(rs.getObject("id")),
                uuid(rs.getObject("operation_id")),
                detectedAt,
                false,
                "El worker durable ya puede reclamar leases expirados; observar antes de forzar ejecución.",
                Map.of(
                        "jobType", safe(rs.getString("job_type")),
                        "attempt", rs.getInt("attempt_count") + "/" + rs.getInt("max_attempts"),
                        "leaseExpiredAt", instantText(rs.getTimestamp("lease_expires_at"))))),
                businessId, Timestamp.from(detectedAt));
    }

    private void detectJourneyMismatch(
            UUID businessId, Instant detectedAt, List<ReconciliationAnomaly> out) {
        jdbc.query("""
                SELECT journey.id AS journey_id,
                       payment.id AS payment_id,
                       payment.operation_id AS payment_operation_id,
                       payment.status AS payment_status,
                       journey.metadata_json ->> 'commercialStage' AS commercial_stage
                  FROM business_payment payment
                  JOIN business_operation payment_operation
                    ON payment_operation.id = payment.operation_id
                   AND payment_operation.business_id = payment.business_id
                  JOIN business_operation journey
                    ON journey.business_id = payment.business_id
                   AND journey.id::text = payment_operation.metadata_json ->> 'commercialJourneyOperationId'
                 WHERE payment.business_id = ?
                   AND (
                       (payment.status = 'SUCCEEDED'
                           AND COALESCE(journey.metadata_json ->> 'commercialStage', '') <> 'PAID')
                       OR COALESCE(journey.metadata_json ->> 'paymentStatus', '') <> payment.status
                   )
                 ORDER BY payment.updated_at ASC
                 LIMIT 100
                """, rs -> out.add(anomaly(
                ReconciliationAnomaly.Type.JOURNEY_STATE_MISMATCH,
                ReconciliationAnomaly.Severity.MEDIUM,
                "COMMERCIAL_JOURNEY",
                uuid(rs.getObject("journey_id")),
                uuid(rs.getObject("payment_operation_id")),
                detectedAt,
                false,
                "Reconciliar metadata del journey solo después de validar pago y cliente; no se muta automáticamente.",
                Map.of(
                        "paymentId", String.valueOf(uuid(rs.getObject("payment_id"))),
                        "paymentStatus", safe(rs.getString("payment_status")),
                        "commercialStage", safe(rs.getString("commercial_stage"))))), businessId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimResult claimRepair(UUID businessId,
                                   ReconciliationAnomaly anomaly,
                                   String idempotencyKey) {
        UUID id = UUID.randomUUID();
        String evidence = new JSONObject(anomaly.evidence()).toString();
        int inserted = jdbc.update("""
                INSERT INTO reconciliation_action (
                    id, business_id, anomaly_type, subject_type, subject_id, operation_id,
                    action, status, idempotency_key, detail_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'STARTED', ?, CAST(? AS jsonb), NOW(), NOW())
                ON CONFLICT (business_id, idempotency_key) DO NOTHING
                """,
                id, businessId, anomaly.type().name(), anomaly.subjectType(), anomaly.subjectId(),
                anomaly.operationId(), anomaly.suggestedAction(), idempotencyKey, evidence);
        if (inserted == 1) return new ClaimResult(id, ClaimState.CLAIMED);

        Optional<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id, status
                  FROM reconciliation_action
                 WHERE business_id = ? AND idempotency_key = ?
                """, businessId, idempotencyKey).stream().findFirst();
        if (existing.isEmpty()) throw new IllegalStateException("Reconciliation claim disappeared");

        UUID existingId = uuid(existing.get().get("id"));
        String status = String.valueOf(existing.get().get("status"));
        if ("FAILED".equals(status)) {
            int retried = jdbc.update("""
                    UPDATE reconciliation_action
                       SET status = 'STARTED',
                           error_message = NULL,
                           completed_at = NULL,
                           updated_at = NOW()
                     WHERE id = ? AND business_id = ? AND status = 'FAILED'
                    """, existingId, businessId);
            if (retried == 1) return new ClaimResult(existingId, ClaimState.CLAIMED);
        }
        return new ClaimResult(existingId,
                "COMPLETED".equals(status) ? ClaimState.ALREADY_COMPLETED : ClaimState.IN_PROGRESS);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID businessId, UUID actionId) {
        jdbc.update("""
                UPDATE reconciliation_action
                   SET status = 'COMPLETED', completed_at = NOW(), updated_at = NOW()
                 WHERE id = ? AND business_id = ?
                """, actionId, businessId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID businessId, UUID actionId, RuntimeException error) {
        String message = error == null || error.getMessage() == null
                ? "Reconciliation repair failed"
                : error.getMessage();
        if (message.length() > 500) message = message.substring(0, 500);
        jdbc.update("""
                UPDATE reconciliation_action
                   SET status = 'FAILED', error_message = ?, completed_at = NOW(), updated_at = NOW()
                 WHERE id = ? AND business_id = ?
                """, message, actionId, businessId);
    }

    private static ReconciliationAnomaly anomaly(
            ReconciliationAnomaly.Type type,
            ReconciliationAnomaly.Severity severity,
            String subjectType,
            UUID subjectId,
            UUID operationId,
            Instant detectedAt,
            boolean safe,
            String action,
            Map<String, String> evidence) {
        return new ReconciliationAnomaly(
                type, severity, subjectType, subjectId, operationId, detectedAt,
                safe, action, Map.copyOf(new LinkedHashMap<>(evidence)));
    }

    private static int severityRank(ReconciliationAnomaly.Severity severity) {
        return switch (severity) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        return UUID.fromString(String.valueOf(value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String instantText(Timestamp value) {
        return value == null ? "" : value.toInstant().toString();
    }

    public record ClaimResult(UUID actionId, ClaimState state) {}
    public enum ClaimState { CLAIMED, ALREADY_COMPLETED, IN_PROGRESS }
}
