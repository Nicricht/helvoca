package cl.helvoca.platform;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformDemoSessionControllerTest {

    @Test
    void platformSessionEndpointsArePlatformAdminOnlyAndNeverAcceptRuntimeBusinessId() {
        RequestMapping mapping = PlatformDemoSessionController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/v1/platform"));

        PreAuthorize authorization = PlatformDemoSessionController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("hasRole('PLATFORM_ADMIN')", authorization.value());

        PlatformDemoSessionService service = mock(PlatformDemoSessionService.class);
        UUID profileId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        PlatformDemoSessionResponse expected = mock(PlatformDemoSessionResponse.class);
        when(service.prepare(profileId)).thenReturn(expected);
        when(service.get(sessionId)).thenReturn(expected);

        PlatformDemoSessionController controller = new PlatformDemoSessionController(service);
        assertSame(expected, controller.prepare(profileId));
        assertSame(expected, controller.get(sessionId));
        verify(service).prepare(profileId);
        verify(service).get(sessionId);
    }
}
