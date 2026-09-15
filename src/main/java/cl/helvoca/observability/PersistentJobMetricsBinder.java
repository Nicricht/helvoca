package cl.helvoca.observability;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Global queue depth gauges. No tenant identifiers are emitted as metric tags. */
@Component
public class PersistentJobMetricsBinder implements MeterBinder {
    private final PersistentJobStore store;

    public PersistentJobMetricsBinder(PersistentJobStore store) {
        this.store = store;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (PersistentJob.Status status : PersistentJob.Status.values()) {
            Gauge.builder("helvoca.jobs.current", this, ignored -> count(status))
                    .description("Current durable jobs grouped by lifecycle status")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .register(registry);
        }
    }

    private double count(PersistentJob.Status status) {
        try {
            return store.countByStatus(status);
        } catch (RuntimeException ignored) {
            // Metrics must never take the application down if the database is
            // temporarily unavailable during a scrape.
            return Double.NaN;
        }
    }
}
