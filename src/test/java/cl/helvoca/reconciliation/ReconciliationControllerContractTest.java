package cl.helvoca.reconciliation;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationControllerContractTest {

    @Test
    void reconciliationSurfaceIsBusinessAdminOnly() {
        RequestMapping mapping =
                ReconciliationController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/v1/reconciliation"));

        PreAuthorize auth =
                ReconciliationController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(auth);
        assertEquals("hasRole('BUSINESS_ADMIN')", auth.value());
    }
}
