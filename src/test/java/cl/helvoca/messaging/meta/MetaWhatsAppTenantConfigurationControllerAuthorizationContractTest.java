package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MetaWhatsAppTenantConfigurationControllerAuthorizationContractTest {

    @Test
    void everyMetaWriteEndpointIsBusinessAdminOnly() {
        List<Method> writeEndpoints = Arrays.stream(
                        MetaWhatsAppTenantConfigurationController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class))
                .toList();

        assertFalse(writeEndpoints.isEmpty(), "Meta controller must expose write endpoints");

        for (Method endpoint : writeEndpoints) {
            PreAuthorize rule = endpoint.getAnnotation(PreAuthorize.class);
            assertNotNull(
                    rule,
                    () -> endpoint.getName() + " must declare an explicit authorization rule");
            assertEquals(
                    "hasRole('BUSINESS_ADMIN')",
                    rule.value(),
                    () -> endpoint.getName() + " must remain BUSINESS_ADMIN-only");
        }
    }
}
