package cl.helvoca.operations;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoicePilotIntegratedReadinessControllerTest {
    @Test
    void exactAdminOnlyReadOnlyEndpointNeverCertifiesCommercialOrLiveAudio() throws Exception {
        var service = mock(VoicePilotIntegratedReadinessService.class);
        var expected = new VoicePilotIntegratedReadinessService.Result(
                "BLOCKED_LOCAL", false, false, "NOT_CHECKED",
                List.of("PILOT_VOICE_EFFECT_BLOCKED"), false, false);
        when(service.inspect("+14355652512")).thenReturn(expected);
        var controller = new VoicePilotIntegratedReadinessController(service);

        assertSame(expected, controller.inspect("+14355652512"));
        verify(service).inspect("+14355652512");
        verifyNoMoreInteractions(service);

        assertEquals("/api/v1/operations",
                VoicePilotIntegratedReadinessController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertEquals("hasRole('BUSINESS_ADMIN')",
                VoicePilotIntegratedReadinessController.class.getAnnotation(PreAuthorize.class).value());
        var endpoint = VoicePilotIntegratedReadinessController.class.getMethod("inspect", String.class);
        assertEquals("/voice-pilot-integrated-preflight",
                endpoint.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("phone", endpoint.getParameters()[0].getAnnotation(RequestParam.class).value());
        assertFalse(expected.liveCallCertified());
        assertFalse(expected.commercialGo());
    }
}
