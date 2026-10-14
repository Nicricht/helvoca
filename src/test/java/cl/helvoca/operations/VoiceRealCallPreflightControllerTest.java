package cl.helvoca.operations;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoiceRealCallPreflightControllerTest {
    @Test
    void protectedReadOnlyEndpointDelegatesWithoutChangingPilotOrDialing() throws Exception {
        var preflight = mock(VoiceRealCallPreflightService.class);
        var expected = new VoiceRealCallPreflightService.Result(
                "BLOCKED", false, false, false, java.util.List.of("PILOT_VOICE_EFFECT_BLOCKED"));
        when(preflight.inspect("+14355652512")).thenReturn(expected);

        var controller = new VoiceRealCallPreflightController(preflight);
        assertSame(expected, controller.inspect("+14355652512"));
        verify(preflight).inspect("+14355652512");
        verifyNoMoreInteractions(preflight);

        assertEquals("/api/v1/operations",
                VoiceRealCallPreflightController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertEquals("hasRole('BUSINESS_ADMIN')",
                VoiceRealCallPreflightController.class.getAnnotation(PreAuthorize.class).value());
        var method = VoiceRealCallPreflightController.class.getMethod("inspect", String.class);
        assertEquals("/voice-real-call-preflight", method.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("phone", method.getParameters()[0].getAnnotation(RequestParam.class).value());
    }
}
