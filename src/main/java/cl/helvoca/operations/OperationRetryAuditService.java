package cl.helvoca.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OperationRetryAuditService {
    public enum Outcome {
        RETRY_SCHEDULED,
        SUCCEEDED_AFTER_RETRY,
        RETRIES_EXHAUSTED,
        FALLBACK_APPLIED,
        UNRESOLVABLE
    }

    private final JdbcTemplate jdbc;

    public OperationRetryAuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID businessId,
                       BusinessOperation.Type operationType,
                       UUID sourceReferenceId,
                       UUID operationId,
                       String toolName,
                       int attemptNo,
                       int maxAttempts,
                       Outcome outcome,
                       OperationPolicyService.FailureClass failureClass,
                       String errorCode,
                       int delayMs) {
        jdbc.update("""
                INSERT INTO business_operation_retry_attempt(
                    business_id, operation_type, source_reference_id, operation_id,
                    tool_name, attempt_no, max_attempts, outcome, failure_class,
                    error_code, delay_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                businessId,
                operationType.name(),
                sourceReferenceId,
                operationId,
                toolName,
                attemptNo,
                maxAttempts,
                outcome.name(),
                failureClass == null ? null : failureClass.name(),
                normalize(errorCode, 80),
                Math.max(0, Math.min(delayMs, 5000)));
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
