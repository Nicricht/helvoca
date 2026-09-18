package cl.helvoca.security;

import cl.helvoca.audit.AuditController;
import cl.helvoca.booking.BookingController;
import cl.helvoca.customer.CustomerController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleAuthorizationContractTest {

    @Test
    void auditIsAdminOnly() {
        PreAuthorize rule = AuditController.class.getAnnotation(PreAuthorize.class);
        assertEquals("hasRole('BUSINESS_ADMIN')", rule.value());
    }

    @Test
    void customerExportIsAdminOnlyWhileCustomerWorkspaceRemainsOperationalForOperator() throws Exception {
        PreAuthorize controllerRule = CustomerController.class.getAnnotation(PreAuthorize.class);
        assertEquals("hasAnyRole('BUSINESS_ADMIN','OPERATOR')", controllerRule.value());

        Method export = CustomerController.class.getMethod("export", String.class);
        PreAuthorize exportRule = export.getAnnotation(PreAuthorize.class);
        assertEquals("hasRole('BUSINESS_ADMIN')", exportRule.value());
    }

    @Test
    void bookingWorkspaceRemainsAvailableToOperator() {
        PreAuthorize rule = BookingController.class.getAnnotation(PreAuthorize.class);
        assertEquals("hasAnyRole('BUSINESS_ADMIN','OPERATOR')", rule.value());
    }
}
