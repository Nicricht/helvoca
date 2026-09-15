package cl.helvoca.jobs;

import cl.helvoca.observability.OperationalMetrics;
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
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(15);

    private final PersistentJobStore store;
    private final PersistentJobHandlerRegistry handlers;
    private final PersistentJobProperties properties;
    private final OperationalMetrics metrics;

    @Autowired
    public PersistentJobService(PersistentJobStore store,
                                PersistentJobHandlerRegistry handlers,
                                PersistentJobProperties properties,
                                OperationalMetrics metrics) {
        this.store = store;
        this.handlers = handlers;
        this.properties = properties;
        this.metrics = metrics;
    }

    // Retained for focused unit tests that do not bootstrap Micrometer.
    public PersistentJobService(PersistentJobStore store,
                                PersistentJobHandlerRegistry handlers,
                                PersistentJobProperties properties) {
        this(store, handlers, properties, null);
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
     * Claims and executes at most one durable job. Claiming is committed before
     * handler execution, so workers do not hold row locks while performing I/O.
     */
    public boolean processOne(String workerId) {
        PersistentJob job = store.claimNext(workerId, Duration.ofSeconds(properties.getLeaseSeconds())).orElse(null);
        if (job == null) return false;

        long started = System.nanoTime();
        String outcome = "unknown";
        MDC.put("jobId", job.id().toString());
        MDC.put("jobType", job.jobType().name());
        try {
            handlers.require(job.jobType()).handle(job);
            store.markSucceeded(job.id(), workerId);
            outcome = "success";
        } catch (PersistentJobHandler.PermanentJobException e) {
            store.markFailed(job, workerId, failureCode(e), safeMessage(e), false, Duration.ZERO);
            outcome = "dead_letter";
        } catch (PersistentJobHandler.RetryableJobException e) {
            store.markFailed(job, workerId, failureCode(e), safeMessage(e), true, backoff(job.attemptCount()));
            outcome = job.attemptCount() >= job.maxAttempts() ? "dead_letter" : "retry";
        } catch (RuntimeException e) {
            // Unknown runtime failures are retried within the bounded attempt budget.
            // A deterministic bug eventually reaches DEAD_LETTER instead of looping forever.
            store.markFailed(job, workerId, failureCode(e), safeMessage(e), true, backoff(job.attemptCount()));
            outcome = job.attemptCount() >= job.maxAttempts() ? "dead_letter" : "retry";
        } finally {
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
