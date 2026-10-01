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
        PlatformDemoTimelineService timeline = mock(PlatformDemoTimelineService.class);
        UUID profileId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        PlatformDemoSessionResponse expected = mock(PlatformDemoSessionResponse.class);
        PlatformDemoTimelineResponse timelineExpected = mock(PlatformDemoTimelineResponse.class);
        when(service.prepare(profileId)).thenReturn(expected);
        when(service.current()).thenReturn(expected);
        when(service.get(sessionId)).thenReturn(expected);
        when(timeline.timeline(sessionId)).thenReturn(timelineExpected);

        PlatformDemoSessionController controller = new PlatformDemoSessionController(service, timeline);
        assertSame(expected, controller.prepare(profileId));
        assertSame(expected, controller.current());
        assertSame(expected, controller.get(sessionId));
        assertSame(timelineExpected, controller.timeline(sessionId));
        verify(service).prepare(profileId);
        verify(service).current();
        verify(service).get(sessionId);
        verify(timeline).timeline(sessionId);
    }
}
