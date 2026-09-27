package cl.helvoca.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InventoryRestockSubscriptionControllerContractTest {

    @Test
    void readsAndSubscriptionIntakeAllowOperatorButCancellationRequiresBusinessAdmin() throws Exception {
        PreAuthorize controllerRule =
                InventoryRestockSubscriptionController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(controllerRule);
        assertEquals("hasAnyRole('BUSINESS_ADMIN','OPERATOR')", controllerRule.value());

        Method cancel = InventoryRestockSubscriptionController.class
                .getMethod("cancel", UUID.class);
        PreAuthorize cancelRule = cancel.getAnnotation(PreAuthorize.class);
        assertNotNull(cancelRule);
        assertEquals("hasRole('BUSINESS_ADMIN')", cancelRule.value());
    }
}
