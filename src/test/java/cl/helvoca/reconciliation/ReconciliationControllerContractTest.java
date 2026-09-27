package cl.helvoca.reconciliation;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationControllerContractTest {

    @Test
    void reconciliationSurfaceIsBusinessAdminOnly() {
        RequestMapping mapping =
                ReconciliationController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/v1/reconciliation"));

        PreAuthorize auth =
                ReconciliationController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(auth);
        assertEquals("hasRole('BUSINESS_ADMIN')", auth.value());
    }

    @Test
    void controllerDelegatesReadsAndRepairsAndRejectsNullBody() {
        ReconciliationService service = mock(ReconciliationService.class);
        ReconciliationController controller = new ReconciliationController(service);
        UUID subjectId = UUID.randomUUID();
        ReconciliationAnomaly anomaly = new ReconciliationAnomaly(
                ReconciliationAnomaly.Type.JOB_STUCK,
                ReconciliationAnomaly.Severity.MEDIUM,
                "PERSISTENT_JOB",
                subjectId,
                null,
                java.time.Instant.parse("2026-09-27T18:30:00Z"),
                false,
                "observe",
                Map.of());
        when(service.detect()).thenReturn(List.of(anomaly));

        assertEquals(List.of(anomaly), controller.anomalies());

        ReconciliationService.RepairResult expected =
                new ReconciliationService.RepairResult(
                        ReconciliationService.RepairStatus.RECOMMENDATION_ONLY,
                        null,
                        false,
                        "observe");
        when(service.repair(
                ReconciliationAnomaly.Type.JOB_STUCK,
                subjectId,
                Boolean.FALSE)).thenReturn(expected);

        ReconciliationController.RepairRequest request =
                new ReconciliationController.RepairRequest(
                        ReconciliationAnomaly.Type.JOB_STUCK,
                        subjectId,
                        Boolean.FALSE);
        assertEquals(expected, controller.repair(request));
        assertThrows(IllegalArgumentException.class, () -> controller.repair(null));

        verify(service).detect();
        verify(service).repair(
                ReconciliationAnomaly.Type.JOB_STUCK,
                subjectId,
                Boolean.FALSE);
    }

}
