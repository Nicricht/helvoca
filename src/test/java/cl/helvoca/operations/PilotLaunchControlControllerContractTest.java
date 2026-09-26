package cl.helvoca.operations;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PilotLaunchControlControllerContractTest {

    @Test
    void readsAllowOperatorButLifecycleMutationsRequireBusinessAdmin() throws Exception {
        RequestMapping mapping =
                PilotLaunchControlController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/v1/operations/pilot-control"));

        PreAuthorize controllerAuth =
                PilotLaunchControlController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(controllerAuth);
        assertEquals("hasAnyRole('BUSINESS_ADMIN','OPERATOR')", controllerAuth.value());

        for (String methodName : new String[]{"configure", "start", "pause", "resume", "complete"}) {
            Method method = Arrays.stream(PilotLaunchControlController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            PreAuthorize auth = method.getAnnotation(PreAuthorize.class);
            assertNotNull(auth, methodName);
            assertEquals("hasRole('BUSINESS_ADMIN')", auth.value(), methodName);
        }
    }
}
