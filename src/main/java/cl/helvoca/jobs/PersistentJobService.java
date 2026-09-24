package cl.helvoca.jobs;

import cl.helvoca.observability.OperationalMetrics;
import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PersistentJobService {
    private static final Logger log = LoggerFactory.getLogger(PersistentJobService.class);
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(15);

    private final PersistentJobStore store;
    private final PersistentJobHandlerRegistry handlers;
    private final PersistentJobProperties properties;
    private final OperationalMetrics metrics;
    private final TenantDatabaseContext databaseContext;

    @Autowired
    public PersistentJobService(PersistentJobStore store,
                                PersistentJobHandlerRegistry handlers,
                                PersistentJobProperties properties,
                                OperationalMetrics metrics,
                                TenantDatabaseContext databaseContext) {
        this.store = store;
        this.handlers = handlers;
        this.properties = properties;
        this.metrics = metrics;
        this.databaseContext = databaseContext;
    }

    // Retained for focused unit tests that do not bootstrap Micrometer/RLS.
    public PersistentJobService(PersistentJobStore store,
                                PersistentJobHandlerRegistry handlers,
                                PersistentJobProperties properties) {
        this(store, handlers, properties, null, null);
    }

    public PersistentJob enqueue(UUID businessId,
                                 UUID operationId,
                                 PersistentJob.Type type,
                                 String idempotencyKey,
                                 String payloadJson) {
        return enqueue(businessId, operationId, type, idempotencyKey, payloadJson,
                properties.getDefaultMaxAttempts(), Instant.now());
    }

    public PersistentJob enqueue(UUID businessId,
                                 UUID operationId,
                                 PersistentJob.Type type,
                                 String idempotencyKey,
                                 String payloadJson,
                                 int maxAttempts,
                                 Instant nextAttemptAt) {
        if (metrics != null) metrics.jobEnqueueRequest(type);
        return store.enqueue(businessId, operationId, type, idempotencyKey, payloadJson, maxAttempts, nextAttemptAt);
    }

    /**
     * Claims globally under controlled SYSTEM scope, then narrows execution to
     * the job tenant before the handler touches business data.
     */
    public boolean processOne(String workerId) {
        PersistentJob job;
        if (databaseContext == null) {
            job = store.claimNext(workerId, Duration.ofSeconds(properties.getLeaseSeconds())).orElse(null);
        } else {
            job = databaseContext.callAsSystem(() ->
                    store.claimNext(workerId, Duration.ofSeconds(properties.getLeaseSeconds())).orElse(null));
        }
        if (job == null) return false;

        if (databaseContext == null) return executeClaimed(job, workerId);
        return databaseContext.callAsTenant(job.businessId(), () -> executeClaimed(job, workerId));
    }

    private boolean executeClaimed(PersistentJob job, String workerId) {
        long started = System.nanoTime();
        String outcome = "unknown";
        String failureCode = null;
        MDC.put("jobId", job.id().toString());
        MDC.put("jobType", job.jobType().name());
        try {
            handlers.require(job.jobType()).handle(job);
            store.markSucceeded(job.id(), workerId);
            outcome = "success";
        } catch (PersistentJobHandler.PermanentJobException e) {
            failureCode = failureCode(e);
            store.markFailed(job, workerId, failureCode, safeMessage(e), false, Duration.ZERO);
            outcome = "dead_letter";
        } catch (PersistentJobHandler.RetryableJobException e) {
            failureCode = failureCode(e);
            store.markFailed(job, workerId, failureCode, safeMessage(e), true,
                    retryDelay(job.attemptCount(), e.retryDelay()));
            outcome = job.attemptCount() >= job.maxAttempts() ? "dead_letter" : "retry";
        } catch (RuntimeException e) {
            // Unknown runtime failures are retried within the bounded attempt budget.
            // A deterministic bug eventually reaches DEAD_LETTER instead of looping forever.
            failureCode = failureCode(e);
            store.markFailed(job, workerId, failureCode, safeMessage(e), true, backoff(job.attemptCount()));
            outcome = job.attemptCount() >= job.maxAttempts() ? "dead_letter" : "retry";
        } finally {
            log.info(
                    "PERSISTENT_JOB_EXECUTION type={} outcome={} attempt={}/{} failureCode={}",
                    job.jobType(),
                    outcome,
                    job.attemptCount(),
                    job.maxAttempts(),
                    failureCode == null ? "NONE" : failureCode);
            if (metrics != null) {
                metrics.jobExecution(job.jobType(), outcome, Duration.ofNanos(System.nanoTime() - started));
            }
            MDC.remove("jobId");
            MDC.remove("jobType");
        }
        return true;
    }

    public int processBatch(String workerId, int maxJobs) {
        int limit = Math.max(1, Math.min(maxJobs, 100));
        int processed = 0;
        while (processed < limit && processOne(workerId)) processed++;
        return processed;
    }

    public boolean cancel(UUID businessId, UUID jobId) {
        return store.cancel(businessId, jobId);
    }

    public List<PersistentJob> recent(UUID businessId) {
        return store.recent(businessId);
    }

    static Duration retryDelay(int attemptCount, Duration requestedMinimum) {
        Duration standard = backoff(attemptCount);
        if (requestedMinimum == null || requestedMinimum.isZero() || requestedMinimum.isNegative()) {
            return standard;
        }
        return requestedMinimum.compareTo(standard) > 0 ? requestedMinimum : standard;
    }

    private static Duration backoff(int attemptCount) {
        int shift = Math.max(0, Math.min(attemptCount - 1, 20));
        long seconds = BASE_BACKOFF.toSeconds() << shift;
        return Duration.ofSeconds(Math.min(seconds, MAX_BACKOFF.toSeconds()));
    }

    private static String failureCode(RuntimeException error) {
        String value = error.getClass().getSimpleName().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]", "_");
        return value.isBlank() ? "JOB_EXECUTION_FAILED" : value;
    }

    private static String safeMessage(RuntimeException error) {
        String value = error.getMessage();
        if (value == null || value.isBlank()) value = "Durable job execution failed.";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
