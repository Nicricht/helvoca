package cl.helvoca.platform;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PlatformCertificationRunControllerTest {

    @Test
    void createPassesOnlyRunIdAndAuthenticatedActor() {
        PlatformCertificationRunService service = mock(PlatformCertificationRunService.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("platform-user");
        var response = new PlatformCertificationRunResponse(
                "latency-api-20260927-010", "PENDING", "platform-user",
                Instant.now(), null, null, null, null, null);
        when(service.create("latency-api-20260927-010", "platform-user")).thenReturn(response);
        PlatformCertificationRunController controller = new PlatformCertificationRunController(service);

        var request = new PlatformCertificationRunRequest("latency-api-20260927-010");
        var actual = controller.create(request, authentication);

        assertEquals(response, actual);
        verify(service).create("latency-api-20260927-010", "platform-user");
    }

    @Test
    void createHandlesMissingAuthenticationFailClosedAtServiceActorDefault() {
        PlatformCertificationRunService service = mock(PlatformCertificationRunService.class);
        var response = new PlatformCertificationRunResponse(
                "latency-api-20260927-011", "PENDING", "platform-admin",
                Instant.now(), null, null, null, null, null);
        when(service.create("latency-api-20260927-011", null)).thenReturn(response);
        PlatformCertificationRunController controller = new PlatformCertificationRunController(service);

        var actual = controller.create(
                new PlatformCertificationRunRequest("latency-api-20260927-011"), null);

        assertEquals(response, actual);
        verify(service).create("latency-api-20260927-011", null);
    }

    @Test
    void getDelegatesByRunId() {
        PlatformCertificationRunService service = mock(PlatformCertificationRunService.class);
        var response = new PlatformCertificationRunResponse(
                "latency-api-20260927-012", "CLAIMED", "platform-user",
                Instant.now(), Instant.now(), Instant.now().plusSeconds(60), null, null, null);
        when(service.get("latency-api-20260927-012")).thenReturn(response);
        PlatformCertificationRunController controller = new PlatformCertificationRunController(service);

        assertEquals(response, controller.get("latency-api-20260927-012"));
    }
}
