package cl.helvoca.platform;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PlatformDemoProfileControllerContractTest {

    @Test
    void demoProfilesArePlatformAdminOnly() {
        RequestMapping mapping =
                PlatformDemoProfileController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/v1/platform/demos"));

        PreAuthorize authorization =
                PlatformDemoProfileController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("hasRole('PLATFORM_ADMIN')", authorization.value());
    }
}
