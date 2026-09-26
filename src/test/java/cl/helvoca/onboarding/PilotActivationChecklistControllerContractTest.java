package cl.helvoca.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PilotActivationChecklistControllerContractTest {

    @Test
    void operatorCanReadButOnlyBusinessAdminCanConfirmActivationChecklist() throws Exception {
        RequestMapping mapping = PilotActivationChecklistController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/v1/onboarding/activation"));

        PreAuthorize controllerAuth = PilotActivationChecklistController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(controllerAuth);
        assertEquals("hasAnyRole('BUSINESS_ADMIN','OPERATOR')", controllerAuth.value());

        Method update = PilotActivationChecklistController.class.getMethod(
                "update", PilotActivationChecklistService.ConfirmationRequest.class);
        PreAuthorize updateAuth = update.getAnnotation(PreAuthorize.class);
        assertNotNull(updateAuth);
        assertEquals("hasRole('BUSINESS_ADMIN')", updateAuth.value());
    }
}
