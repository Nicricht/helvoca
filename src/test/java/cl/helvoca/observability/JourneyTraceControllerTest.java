package cl.helvoca.observability;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JourneyTraceControllerTest {

    @Test
    void journeyDebugEndpointIsBusinessAdminOnly() {
        PreAuthorize rule = JourneyTraceController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(rule);
        assertEquals("hasRole('BUSINESS_ADMIN')", rule.value());
    }

    @Test
    void controllerDelegatesReadOnlyLookup() {
        JourneyTraceService service = mock(JourneyTraceService.class);
        JourneyTraceController controller = new JourneyTraceController(service);
        JourneyTraceService.TraceView expected = mock(JourneyTraceService.TraceView.class);
        when(service.get("journey-123")).thenReturn(expected);

        assertSame(expected, controller.get("journey-123"));
        verify(service).get("journey-123");
    }
}
