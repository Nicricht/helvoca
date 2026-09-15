package cl.helvoca.observability;

import cl.helvoca.jobs.PersistentJob;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OperationalMetricsTest {
    @Test
    void jobMetricsUseOnlyBoundedTypeAndOutcomeTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(registry);

        metrics.jobEnqueueRequest(PersistentJob.Type.CALENDAR_EVENT_SYNC);
        metrics.jobExecution(PersistentJob.Type.CALENDAR_EVENT_SYNC, "success", Duration.ofMillis(25));

        assertEquals(1.0, registry.get("helvoca.jobs.enqueue.requests")
                .tag("type", "calendar_event_sync").counter().count());
        assertEquals(1.0, registry.get("helvoca.jobs.executions")
                .tag("type", "calendar_event_sync")
                .tag("outcome", "success")
                .counter().count());
        assertNotNull(registry.get("helvoca.jobs.execution.duration")
                .tag("type", "calendar_event_sync")
                .tag("outcome", "success")
                .timer());
    }

    @Test
    void unknownOutcomeIsCollapsedToBoundedTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(registry);

        metrics.jobExecution(PersistentJob.Type.OUTBOUND_MESSAGE_DISPATCH,
                "arbitrary-provider-error-123", Duration.ZERO);

        assertEquals(1.0, registry.get("helvoca.jobs.executions")
                .tag("type", "outbound_message_dispatch")
                .tag("outcome", "unknown")
                .counter().count());
    }
}
