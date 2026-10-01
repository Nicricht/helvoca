package cl.helvoca.platform;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformDemoReadinessControllerTest {

    @Test
    void readinessEndpointIsPlatformAdminOnlyAndDelegates() {
        RequestMapping mapping = PlatformDemoReadinessController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/v1/platform/demos/readiness"));

        PreAuthorize authorization = PlatformDemoReadinessController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("hasRole('PLATFORM_ADMIN')", authorization.value());

        PlatformDemoReadinessService service = mock(PlatformDemoReadinessService.class);
        PlatformDemoReadinessResponse expected = new PlatformDemoReadinessResponse(
                false, null,
                new PlatformDemoReadinessResponse.ReadinessItem("NOT_CONFIGURED", "runtime"),
                new PlatformDemoReadinessResponse.ReadinessItem("NOT_CONFIGURED", "voice"),
                new PlatformDemoReadinessResponse.ReadinessItem("NOT_CONFIGURED", "ai"),
                new PlatformDemoReadinessResponse.ReadinessItem("NOT_CONFIGURED", "data"),
                new PlatformDemoReadinessResponse.ReadinessItem("NOT_CONFIGURED", "ops"),
                new PlatformDemoReadinessResponse.ReadinessItem("NOT_CONFIGURED", "wa"),
                new PlatformDemoReadinessResponse.ReadinessItem("SANDBOX_ONLY", "payment"),
                new PlatformDemoReadinessResponse.ReadinessItem("DISARMED", "effects"));
        when(service.readiness()).thenReturn(expected);

        assertSame(expected, new PlatformDemoReadinessController(service).readiness());
        verify(service).readiness();
    }
}
