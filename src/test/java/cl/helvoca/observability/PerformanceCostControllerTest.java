package cl.helvoca.observability;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerformanceCostControllerTest {

    @Test
    void performanceCostEndpointIsBusinessAdminOnly() {
        PreAuthorize rule = PerformanceCostController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(rule);
        assertEquals("hasRole('BUSINESS_ADMIN')", rule.value());
    }

    @Test
    void controllerDelegatesRequestedWindow() {
        PerformanceCostService service = mock(PerformanceCostService.class);
        PerformanceCostController controller = new PerformanceCostController(service);
        Instant from = Instant.parse("2026-09-27T00:00:00Z");
        Instant to = Instant.parse("2026-09-27T01:00:00Z");
        PerformanceCostService.Snapshot expected = mock(PerformanceCostService.Snapshot.class);
        when(service.snapshot(from, to)).thenReturn(expected);

        assertSame(expected, controller.snapshot(from, to));
        verify(service).snapshot(from, to);
    }
}
