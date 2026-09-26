package cl.helvoca.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BusinessActivationGuideControllerContractTest {

    @Test
    void activationGuideIsTenantAuthenticatedAndReadableByOperators() {
        RequestMapping mapping =
                BusinessActivationGuideController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/v1/onboarding/guide"));

        PreAuthorize auth =
                BusinessActivationGuideController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(auth);
        assertEquals("hasAnyRole('BUSINESS_ADMIN','OPERATOR')", auth.value());
    }
}
