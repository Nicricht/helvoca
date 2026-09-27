package cl.helvoca.operations;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PilotGoNoGoControllerContractTest {

    @Test
    void exposesReadOnlyTenantScopedPreflightToAdminAndOperator() throws Exception {
        RequestMapping mapping = PilotGoNoGoController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/v1/operations/pilot-preflight"));

        PreAuthorize auth = PilotGoNoGoController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(auth);
        assertEquals("hasAnyRole('BUSINESS_ADMIN','OPERATOR')", auth.value());

        Method method = PilotGoNoGoController.class.getMethod("evaluate");
        assertNotNull(method.getAnnotation(GetMapping.class));
    }
}
