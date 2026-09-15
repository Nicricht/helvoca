package cl.helvoca.operations;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLTransientException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
public class SafeOperationRetryEngine {
    private static final int BASE_DELAY_MS = 100;
    private static final int MAX_DELAY_MS = 1000;

    private static final Set<String> TRANSIENT_CODES = Set.of(
            "COMMERCIAL_OPERATION_FAILED",
            "TOOL_EXECUTION_FAILED",
            "REQUEST_OPERATION_FAILED",
            "BOOKING_OPERATION_SYNC_FAILED",
            "PAYMENT_PROVIDER_FAILED",
            "TEMPORARY_BACKEND_FAILURE",
            "RETRYABLE_CONFLICT");

    private static final Set<String> UNRESOLVABLE_CODES = Set.of(
            "ORDER_CANNOT_BE_CANCELLED",
            "PAYMENT_PROVIDER_NOT_CONFIGURED",
            "PAYMENT_PROVIDER_CONFIGURATION_INVALID");

    private static final Map<String, String> FALLBACK_ACTIONS = Map.ofEntries(
            Map.entry("BOOKING_SLOT_UNAVAILABLE", "REFRESH_AVAILABILITY"),
            Map.entry("BUSINESS_CLOSED", "REFRESH_AVAILABILITY"),
            Map.entry("ORDER_TOTAL_CHANGED", "REQUOTE_AND_RECONFIRM"),
            Map.entry("PAYMENT_TARGET_CHANGED", "REQUOTE_AND_RECONFIRM"),
            Map.entry("CUSTOMER_NOT_REGISTERED", "COLLECT_CUSTOMER_CONTEXT"),
            Map.entry("DELIVERY_ADDRESS_NOT_COVERED", "OFFER_ALTERNATIVE_FULFILLMENT"),
            Map.entry("DELIVERY_NOT_COVERED", "OFFER_ALTERNATIVE_FULFILLMENT"),
            Map.entry("TOOL_DISABLED", "USE_AVAILABLE_CAPABILITY"),
            Map.entry("AUTOMATION_DISABLED", "READ_ONLY_OR_EXPLAIN_POLICY"),
            Map.entry("INVALID_ARGUMENT", "REPLAN_WITH_AVAILABLE_TOOLS"),
            Map.entry("BOOKING_NOT_FOUND", "REFRESH_CUSTOMER_STATE"),
            Map.entry("ORDER_NOT_FOUND", "REFRESH_CUSTOMER_STATE"));

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private record Failure(OperationPolicyService.FailureClass failureClass,
                           String errorCode,
                           String fallbackAction) {
    }

    private record Attempt(String rawResult, Failure failure) {
    }

    private final OperationPolicyService policies;
    private final OperationRetryAuditService audit;
    private final PlatformTransactionManager transactionManager;
    private final Sleeper sleeper;

    @Autowired
    public SafeOperationRetryEngine(OperationPolicyService policies,
                                    OperationRetryAuditService audit,
                                    PlatformTransactionManager transactionManager) {
        this(policies, audit, transactionManager, Thread::sleep);
    }

    SafeOperationRetryEngine(OperationPolicyService policies,
                             OperationRetryAuditService audit,
                             PlatformTransactionManager transactionManager,
                             Sleeper sleeper) {
        this.policies = policies;
        this.audit = audit;
        this.transactionManager = transactionManager;
        this.sleeper = sleeper;
    }

    public String execute(UUID businessId,
                          BusinessOperation.Type operationType,
                          UUID sourceReferenceId,
                          String toolName,
                          Supplier<String> action) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        if (operationType == null) throw new IllegalArgumentException("operationType is required");
        if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("toolName is required");
        if (action == null) throw new IllegalArgumentException("action is required");

        OperationPolicyService.Policy policy = policies.resolve(businessId, operationType);
        int automaticRetries = policy.retriesAutomatically() ? policy.maxAutoRetries() : 0;
        int maxAttempts = 1 + automaticRetries;

        for (int attemptNo = 1; attemptNo <= maxAttempts; attemptNo++) {
            Attempt attempt = executeAttempt(action);
            Failure failure = attempt.failure();

            if (failure == null) {
                if (attemptNo > 1) {
                    safeAudit(businessId, operationType, sourceReferenceId,
                            operationId(attempt.rawResult()), toolName, attemptNo, maxAttempts,
                            OperationRetryAuditService.Outcome.SUCCEEDED_AFTER_RETRY,
                            null, null, 0);
                }
                return attempt.rawResult();
            }

            if (failure.failureClass() == OperationPolicyService.FailureClass.TRANSIENT) {
                if (attemptNo < maxAttempts) {
                    int delayMs = backoffMs(attemptNo);
                    safeAudit(businessId, operationType, sourceReferenceId,
                            operationId(attempt.rawResult()), toolName, attemptNo, maxAttempts,
                            OperationRetryAuditService.Outcome.RETRY_SCHEDULED,
                            failure.failureClass(), failure.errorCode(), delayMs);
                    if (!sleep(delayMs)) {
                        safeAudit(businessId, operationType, sourceReferenceId,
                                operationId(attempt.rawResult()), toolName, attemptNo, maxAttempts,
                                OperationRetryAuditService.Outcome.RETRIES_EXHAUSTED,
                                failure.failureClass(), "RETRY_INTERRUPTED", 0);
                        return enrichFailure(attempt.rawResult(), failure,
                                "RETRY_LATER", false, attemptNo - 1);
                    }
                    continue;
                }

                safeAudit(businessId, operationType, sourceReferenceId,
                        operationId(attempt.rawResult()), toolName, attemptNo, maxAttempts,
                        OperationRetryAuditService.Outcome.RETRIES_EXHAUSTED,
                        failure.failureClass(), failure.errorCode(), 0);
                return enrichFailure(attempt.rawResult(), failure,
                        "RETRY_LATER", false, attemptNo - 1);
            }

            if (failure.failureClass() == OperationPolicyService.FailureClass.RESOLVABLE_WITH_FALLBACK) {
                safeAudit(businessId, operationType, sourceReferenceId,
                        operationId(attempt.rawResult()), toolName, attemptNo, maxAttempts,
                        OperationRetryAuditService.Outcome.FALLBACK_APPLIED,
                        failure.failureClass(), failure.errorCode(), 0);
                return enrichFailure(attempt.rawResult(), failure,
                        failure.fallbackAction(), false, attemptNo - 1);
            }

            boolean humanEscalation = policy.shouldEscalate(failure.failureClass());
            safeAudit(businessId, operationType, sourceReferenceId,
                    operationId(attempt.rawResult()), toolName, attemptNo, maxAttempts,
                    OperationRetryAuditService.Outcome.UNRESOLVABLE,
                    failure.failureClass(), failure.errorCode(), 0);
            return enrichFailure(attempt.rawResult(), failure,
                    humanEscalation ? "HUMAN_HANDOFF" : "STOP_SAFELY",
                    humanEscalation, attemptNo - 1);
        }

        return error("AUTOMATION_RETRY_EXHAUSTED",
                "La operación no pudo completarse de forma segura.").toString();
    }

    private Attempt executeAttempt(Supplier<String> action) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);

        try {
            Attempt attempt = tx.execute(status -> {
                String raw = action.get();
                Failure failure = classifyResult(raw);
                if (failure != null
                        && failure.failureClass() == OperationPolicyService.FailureClass.TRANSIENT) {
                    // A transient failure result may have been produced after partial work was
                    // attempted. Never commit that attempt before trying again.
                    status.setRollbackOnly();
                }
                return new Attempt(raw, failure);
            });
            if (attempt != null) return attempt;
            return new Attempt(error("TOOL_EXECUTION_FAILED",
                    "La operación no pudo completarse en el backend.").toString(),
                    transientFailure("TOOL_EXECUTION_FAILED"));
        } catch (RuntimeException e) {
            Failure failure = classifyThrowable(e);
            String raw = failure.failureClass() == OperationPolicyService.FailureClass.TRANSIENT
                    ? error("TEMPORARY_BACKEND_FAILURE",
                    "La operación encontró un problema temporal y se reintentará de forma segura.").toString()
                    : error("OPERATION_EXECUTION_FAILED",
                    "La operación no pudo completarse de forma segura.").toString();
            return new Attempt(raw, failure);
        }
    }

    private static Failure classifyResult(String rawResult) {
        if (rawResult == null || rawResult.isBlank()) {
            return transientFailure("TOOL_EXECUTION_FAILED");
        }
        try {
            JSONObject root = new JSONObject(rawResult);
            if (root.optBoolean("success", false)) return null;
            JSONObject error = root.optJSONObject("error");
            String code = error == null ? "UNKNOWN_OPERATION_FAILURE" : error.optString("code", "UNKNOWN_OPERATION_FAILURE");

            if (TRANSIENT_CODES.contains(code)
                    || code.endsWith("_TIMEOUT")
                    || code.endsWith("_TEMPORARY_FAILURE")) {
                return transientFailure(code);
            }
            if (UNRESOLVABLE_CODES.contains(code)) {
                return new Failure(OperationPolicyService.FailureClass.UNRESOLVABLE, code, null);
            }
            String fallback = FALLBACK_ACTIONS.getOrDefault(code, "REPLAN_WITH_AVAILABLE_TOOLS");
            return new Failure(OperationPolicyService.FailureClass.RESOLVABLE_WITH_FALLBACK, code, fallback);
        } catch (Exception e) {
            return transientFailure("MALFORMED_TOOL_RESULT");
        }
    }

    private static Failure classifyThrowable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalArgumentException) {
                return new Failure(OperationPolicyService.FailureClass.RESOLVABLE_WITH_FALLBACK,
                        "INVALID_ARGUMENT", "REPLAN_WITH_AVAILABLE_TOOLS");
            }
            if (current instanceof TransientDataAccessException
                    || current instanceof CannotCreateTransactionException
                    || current instanceof SQLTransientException
                    || current instanceof SocketTimeoutException
                    || current instanceof ConnectException
                    || current instanceof TimeoutException) {
                return transientFailure(current.getClass().getSimpleName());
            }
            current = current.getCause();
        }
        return new Failure(OperationPolicyService.FailureClass.UNRESOLVABLE,
                "UNEXPECTED_OPERATION_FAILURE", null);
    }

    private static Failure transientFailure(String code) {
        return new Failure(OperationPolicyService.FailureClass.TRANSIENT, code, null);
    }

    private static int backoffMs(int failedAttemptNo) {
        long delay = (long) BASE_DELAY_MS << Math.max(0, failedAttemptNo - 1);
        return (int) Math.min(delay, MAX_DELAY_MS);
    }

    private boolean sleep(int delayMs) {
        try {
            sleeper.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void safeAudit(UUID businessId,
                           BusinessOperation.Type operationType,
                           UUID sourceReferenceId,
                           UUID operationId,
                           String toolName,
                           int attemptNo,
                           int maxAttempts,
                           OperationRetryAuditService.Outcome outcome,
                           OperationPolicyService.FailureClass failureClass,
                           String errorCode,
                           int delayMs) {
        try {
            audit.record(businessId, operationType, sourceReferenceId, operationId,
                    toolName, attemptNo, maxAttempts, outcome, failureClass, errorCode, delayMs);
        } catch (RuntimeException ignored) {
            // Observability must never turn a safe operation into a failed operation.
        }
    }

    private static UUID operationId(String rawResult) {
        if (rawResult == null || rawResult.isBlank()) return null;
        try {
            JSONObject root = new JSONObject(rawResult);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return null;
            String raw = data.optString("operationId", null);
            if (raw == null || raw.isBlank()) raw = data.optString("paymentOperationId", null);
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String enrichFailure(String rawResult,
                                        Failure failure,
                                        String fallbackAction,
                                        boolean humanEscalation,
                                        int retryCount) {
        JSONObject root;
        try {
            root = rawResult == null || rawResult.isBlank()
                    ? error("OPERATION_EXECUTION_FAILED", "La operación no pudo completarse de forma segura.")
                    : new JSONObject(rawResult);
        } catch (Exception e) {
            root = error("OPERATION_EXECUTION_FAILED", "La operación no pudo completarse de forma segura.");
        }
        root.put("automation", new JSONObject()
                .put("failureClass", failure.failureClass().name())
                .put("fallbackAction", fallbackAction == null ? JSONObject.NULL : fallbackAction)
                .put("humanEscalation", humanEscalation)
                .put("retryCount", Math.max(0, retryCount)));
        return root.toString();
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }
}
