package cl.helvoca.reconciliation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ReconciliationAnomaly(
        Type type,
        Severity severity,
        String subjectType,
        UUID subjectId,
        UUID operationId,
        Instant detectedAt,
        boolean safeRepairAvailable,
        String suggestedAction,
        Map<String, String> evidence) {

    public enum Type {
        PAYMENT_SUCCEEDED_ORDER_NOT_CONFIRMED,
        PAYMENT_SUCCEEDED_INVENTORY_RESERVED,
        PAYMENT_TERMINAL_INVENTORY_RESERVED,
        OUTBOUND_STUCK_PREPARED,
        ORPHAN_RESERVATION,
        RECOVERABLE_WEBHOOK_FAILED,
        JOB_STUCK,
        JOURNEY_STATE_MISMATCH
    }

    public enum Severity {
        CRITICAL, HIGH, MEDIUM, LOW
    }
}
