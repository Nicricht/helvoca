package cl.helvoca.business;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BusinessAppearanceControllerAuthorizationContractTest {

    @Test
    void appearanceMutationIsBusinessAdminOnly() throws Exception {
        Method method = BusinessController.class.getMethod(
                "updateAppearance",
                BusinessAppearanceRequest.class);

        PutMapping mapping = method.getAnnotation(PutMapping.class);
        assertNotNull(mapping);
        assertEquals("/appearance", mapping.value()[0]);

        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("hasRole('BUSINESS_ADMIN')", authorization.value());
    }
}
