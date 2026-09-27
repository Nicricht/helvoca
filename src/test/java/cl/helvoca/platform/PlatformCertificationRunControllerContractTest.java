package cl.helvoca.platform;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PlatformCertificationRunControllerContractTest {

    @Test
    void endpointIsPlatformAdminOnlyAndRequestAcceptsOnlyRunId() {
        RequestMapping mapping =
                PlatformCertificationRunController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value())
                .contains("/api/v1/platform/certification-runs"));

        PreAuthorize authorization =
                PlatformCertificationRunController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("hasRole('PLATFORM_ADMIN')", authorization.value());

        RecordComponent[] fields = PlatformCertificationRunRequest.class.getRecordComponents();
        assertEquals(1, fields.length);
        assertEquals("runId", fields[0].getName());
        assertEquals(String.class, fields[0].getType());
    }
}
