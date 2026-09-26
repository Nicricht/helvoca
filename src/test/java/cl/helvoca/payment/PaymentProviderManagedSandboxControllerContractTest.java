package cl.helvoca.payment;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class PaymentProviderManagedSandboxControllerContractTest {

    @Test
    void managedSandboxStatusAllowsOperatorButMutationsRequireBusinessAdmin() throws Exception {
        Method status = PaymentProviderConfigController.class.getMethod("managedSandbox");
        Method enable = PaymentProviderConfigController.class.getMethod("enableManagedSandbox");
        Method disable = PaymentProviderConfigController.class.getMethod("disableManagedSandbox");

        assertEquals(
                "hasAnyRole('BUSINESS_ADMIN','OPERATOR')",
                status.getAnnotation(PreAuthorize.class).value());
        assertEquals(
                "hasRole('BUSINESS_ADMIN')",
                enable.getAnnotation(PreAuthorize.class).value());
        assertEquals(
                "hasRole('BUSINESS_ADMIN')",
                disable.getAnnotation(PreAuthorize.class).value());
    }
}
