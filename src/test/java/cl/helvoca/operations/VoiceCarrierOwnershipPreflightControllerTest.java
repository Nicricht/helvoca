package cl.helvoca.operations;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoiceCarrierOwnershipPreflightControllerTest {
    @Test
    void onlyBusinessAdminsCanInvokeTheExplicitReadOnlyCarrierLookup() throws Exception {
        VoiceCarrierOwnershipPreflightService service = mock(VoiceCarrierOwnershipPreflightService.class);
        var result = new VoiceCarrierOwnershipPreflightService.Result(
                "LOCAL_BINDING_INCOMPLETE", "NOT_CHECKED", false, false, false, false);
        when(service.inspect("+14355652512")).thenReturn(result);

        VoiceCarrierOwnershipPreflightController controller =
                new VoiceCarrierOwnershipPreflightController(service);
        assertSame(result, controller.inspect("+14355652512"));
        verify(service).inspect("+14355652512");
        verifyNoMoreInteractions(service);

        assertEquals("/api/v1/operations",
                VoiceCarrierOwnershipPreflightController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertEquals("hasRole('BUSINESS_ADMIN')",
                VoiceCarrierOwnershipPreflightController.class.getAnnotation(PreAuthorize.class).value());
        var endpoint = VoiceCarrierOwnershipPreflightController.class.getMethod("inspect", String.class);
        assertEquals("/voice-carrier-ownership-preflight", endpoint.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("phone", endpoint.getParameters()[0].getAnnotation(RequestParam.class).value());
    }
}
