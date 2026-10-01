package cl.helvoca.platform;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DemoSessionTest {

    @Test
    void lifecycleDefaultsSnapshotsAndTimestampsAreFailClosedAndStable() {
        DemoSession fresh = new DemoSession();
        fresh.prePersist();
        assertNotNull(fresh.getCorrelationId());
        assertNotNull(fresh.getCreatedAt());
        assertNotNull(fresh.getUpdatedAt());

        var created = fresh.getCreatedAt();
        var correlation = fresh.getCorrelationId();
        fresh.prePersist();
        assertEquals(created, fresh.getCreatedAt());
        assertEquals(correlation, fresh.getCorrelationId());

        DemoSession session = DemoSession.preparing(UUID.randomUUID(), UUID.randomUUID(), "rev-1");
        session.prePersist();
        assertEquals(DemoSessionState.PREPARING, session.getState());

        session.setReadinessSnapshot(null);
        assertEquals(Map.of(), session.getReadinessSnapshot());
        session.setReadinessSnapshot(Map.of("runtime", "READY"));
        assertEquals("READY", session.getReadinessSnapshot().get("runtime"));

        session.markStaged();
        assertNotNull(session.getStagedAt());

        session.markReady();
        assertEquals(DemoSessionState.READY, session.getState());
        assertNull(session.getFailureReason());

        session.markActive();
        var started = session.getStartedAt();
        assertNotNull(started);
        session.markActive();
        assertEquals(started, session.getStartedAt());

        session.markFinished();
        assertEquals(DemoSessionState.FINISHED, session.getState());
        assertNotNull(session.getFinishedAt());

        session.markFailed(null);
        assertEquals("Demo preparation failed", session.getFailureReason());
        session.markFailed("   ");
        assertEquals("Demo preparation failed", session.getFailureReason());
        session.markFailed("provider unavailable");
        assertEquals("provider unavailable", session.getFailureReason());

        session.preUpdate();
        assertNotNull(session.getUpdatedAt());
    }
}
