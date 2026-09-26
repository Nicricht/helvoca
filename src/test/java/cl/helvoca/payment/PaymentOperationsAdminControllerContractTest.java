package cl.helvoca.payment;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PaymentOperationsAdminControllerContractTest {

    @Test
    void controllerIsTenantAuthenticatedAndReconcileIsAdminOnly() throws Exception {
        RequestMapping mapping =
                PaymentOperationsAdminController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/v1/payment-operations"));

        PreAuthorize controllerAuth =
                PaymentOperationsAdminController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(controllerAuth);
        assertEquals("hasAnyRole('BUSINESS_ADMIN','OPERATOR')", controllerAuth.value());

        Method reconcile =
                PaymentOperationsAdminController.class.getMethod("reconcile", java.util.UUID.class);
        PreAuthorize reconcileAuth = reconcile.getAnnotation(PreAuthorize.class);
        assertNotNull(reconcileAuth);
        assertEquals("hasRole('BUSINESS_ADMIN')", reconcileAuth.value());
    }
}
