package cl.helvoca.platform;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DemoSessionBranchCoverageTest {

    @Test
    void defaultsAndNullSafeSnapshotsCoverSessionBoundaryBranches() {
        UUID profileId = UUID.randomUUID();
        UUID runtimeId = UUID.randomUUID();

        DemoSession session = DemoSession.preparing(profileId, runtimeId, "rev", null, null);
        assertEquals("platform", session.getOperator());
        assertEquals(Map.of(), session.getConfigurationSnapshot());

        session.setConfigurationSnapshot(null);
        session.setReadinessSnapshot(null);
        assertEquals(Map.of(), session.getConfigurationSnapshot());
        assertEquals(Map.of(), session.getReadinessSnapshot());

        ReflectionTestUtils.setField(session, "correlationId", null);
        ReflectionTestUtils.setField(session, "operator", " ");
        ReflectionTestUtils.setField(session, "externalEffectsState", null);
        ReflectionTestUtils.setField(session, "paymentState", "");
        ReflectionTestUtils.setField(session, "createdAt", null);
        session.prePersist();

        assertNotNull(session.getCorrelationId());
        assertEquals("platform", session.getOperator());
        assertEquals("DISARMED", session.getExternalEffectsState());
        assertEquals("SANDBOX_ONLY", session.getPaymentState());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getUpdatedAt());
    }

    @Test
    void persistedExplicitValuesArePreservedAndUpdatesAdvanceTimestamp() {
        DemoSession session = DemoSession.preparing(
                UUID.randomUUID(), UUID.randomUUID(), "rev", "Admin@Example.CL", Map.of("name", "Demo"));
        UUID correlation = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-01T00:00:00Z");
        ReflectionTestUtils.setField(session, "correlationId", correlation);
        ReflectionTestUtils.setField(session, "externalEffectsState", "DISARMED");
        ReflectionTestUtils.setField(session, "paymentState", "SANDBOX_ONLY");
        ReflectionTestUtils.setField(session, "createdAt", created);

        session.prePersist();

        assertEquals(correlation, session.getCorrelationId());
        assertEquals("admin@example.cl", session.getOperator());
        assertEquals(created, session.getCreatedAt());
        Instant before = session.getUpdatedAt();
        session.preUpdate();
        assertNotNull(session.getUpdatedAt());
        assertFalse(session.getUpdatedAt().isBefore(before));
    }

    @Test
    void failureReasonUsesSafeFallbackForNullAndBlankAndKeepsExplicitReason() {
        DemoSession session = DemoSession.preparing(UUID.randomUUID(), UUID.randomUUID(), "rev");

        session.markFailed(null);
        assertEquals("Demo preparation failed", session.getFailureReason());

        session.markFailed("   ");
        assertEquals("Demo preparation failed", session.getFailureReason());

        session.markFailed("provider unavailable");
        assertEquals("provider unavailable", session.getFailureReason());
    }

    @Test
    void lifecycleMethodsPreserveFirstStartAndSetTerminalTime() {
        DemoSession session = DemoSession.preparing(UUID.randomUUID(), UUID.randomUUID(), "rev");
        session.markStaged();
        session.markReady();
        session.markActive();
        Instant firstStart = session.getStartedAt();
        assertNotNull(firstStart);

        session.markActive();
        assertEquals(firstStart, session.getStartedAt());

        session.markFinished();
        assertEquals(DemoSessionState.FINISHED, session.getState());
        assertNotNull(session.getFinishedAt());

        DemoSession aborted = DemoSession.preparing(UUID.randomUUID(), UUID.randomUUID(), "rev");
        aborted.markReady();
        aborted.markAborted();
        assertEquals(DemoSessionState.ABORTED, aborted.getState());
        assertNotNull(aborted.getFinishedAt());
    }
}
