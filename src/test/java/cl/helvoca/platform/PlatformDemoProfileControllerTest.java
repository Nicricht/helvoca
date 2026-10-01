package cl.helvoca.platform;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PlatformDemoProfileControllerTest {

    @Test
    void delegatesProfileCrudWithoutAcceptingTenantOrProviderIdentity() {
        PlatformDemoProfileService service = mock(PlatformDemoProfileService.class);
        PlatformDemoProfileController controller = new PlatformDemoProfileController(service);
        UUID id = UUID.randomUUID();
        PlatformDemoProfileRequest request = new PlatformDemoProfileRequest(
                "Sushi Akira", "Sushi Akira", "America/Santiago", "es",
                Map.of(), Map.of(), Map.of(), "Hola", null, List.of("ORDER"), null, Map.of());
        PlatformDemoProfileResponse response = new PlatformDemoProfileResponse(
                id, "Sushi Akira", "Sushi Akira", "America/Santiago", "es",
                Map.of(), Map.of(), Map.of(), "Hola", null, List.of("ORDER"), null, Map.of(),
                null, null);

        when(service.list()).thenReturn(List.of(response));
        when(service.create(request)).thenReturn(response);
        when(service.update(id, request)).thenReturn(response);

        assertEquals(List.of(response), controller.list());
        assertEquals(response, controller.create(request));
        assertEquals(response, controller.update(id, request));
        controller.delete(id);

        verify(service).list();
        verify(service).create(request);
        verify(service).update(id, request);
        verify(service).delete(id);
        verifyNoMoreInteractions(service);
    }
}
