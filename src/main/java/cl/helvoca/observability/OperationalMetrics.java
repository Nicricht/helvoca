package cl.helvoca.observability;

import cl.helvoca.jobs.PersistentJob;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Low-cardinality domain metrics for Helvoca.
 *
 * Tenant identifiers, operation ids, phone numbers and provider payload values
 * are deliberately excluded from metric tags. Tenant-specific diagnostics live
 * behind authenticated APIs instead of Prometheus labels.
 */
@Component
public class OperationalMetrics {
    private final MeterRegistry registry;

    public OperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void jobEnqueueRequest(PersistentJob.Type type) {
        Counter.builder("helvoca.jobs.enqueue.requests")
                .description("Durable job enqueue requests, including idempotent replays")
                .tag("type", tag(type))
                .register(registry)
                .increment();
    }

    public void jobExecution(PersistentJob.Type type, String outcome, Duration duration) {
        String safeOutcome = safeOutcome(outcome);
        Counter.builder("helvoca.jobs.executions")
                .description("Durable job executions by bounded outcome")
                .tag("type", tag(type))
                .tag("outcome", safeOutcome)
                .register(registry)
                .increment();

        Timer.builder("helvoca.jobs.execution.duration")
                .description("Durable job handler execution time")
                .tag("type", tag(type))
                .tag("outcome", safeOutcome)
                .register(registry)
                .record(duration == null ? Duration.ZERO : duration);
    }

    private static String tag(PersistentJob.Type type) {
        return type == null ? "unknown" : type.name().toLowerCase(Locale.ROOT);
    }

    private static String safeOutcome(String outcome) {
        if (outcome == null) return "unknown";
        return switch (outcome) {
            case "success", "retry", "dead_letter", "lease_lost" -> outcome;
            default -> "unknown";
        };
    }
}
