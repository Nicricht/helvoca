package cl.helvoca.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoiceSetupReadinessControllerContractTest {
    @Test
    void tenantRoleProtectedReadOnlyEndpointDelegatesWithoutMutation() throws Exception {
        RequestMapping route = VoiceSetupReadinessController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/api/v1/onboarding/voice-readiness"}, route.value());
        assertEquals("hasAnyRole('BUSINESS_ADMIN','OPERATOR')",
                VoiceSetupReadinessController.class.getAnnotation(PreAuthorize.class).value());
        assertNotNull(VoiceSetupReadinessController.class.getMethod("current").getAnnotation(GetMapping.class));

        VoiceSetupReadinessService service = mock(VoiceSetupReadinessService.class);
        var guide = new VoiceSetupReadinessService.Guide(
                "CONFIGURATION_INCOMPLETE", false, false, false, null, List.of());
        when(service.current()).thenReturn(guide);

        assertSame(guide, new VoiceSetupReadinessController(service).current());
        verify(service).current();
        verifyNoMoreInteractions(service);
    }
}
