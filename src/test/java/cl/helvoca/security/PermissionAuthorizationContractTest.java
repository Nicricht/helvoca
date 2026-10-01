package cl.helvoca.security;

import cl.helvoca.booking.BookingController;
import cl.helvoca.customer.CustomerController;
import cl.helvoca.inventory.InventoryController;
import cl.helvoca.messaging.MessagingConversationQueryController;
import cl.helvoca.operations.CommercialOperationsAdminController;
import cl.helvoca.operations.SalesAnalyticsController;
import cl.helvoca.user.TeamInvitationAdminController;
import cl.helvoca.user.UserAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PermissionAuthorizationContractTest {

    @Test
    void teamManagementUsesPermissionAuthority() {
        assertEquals("hasAuthority('PERM_TEAM_MANAGE')",
                TeamInvitationAdminController.class.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('PERM_TEAM_MANAGE')",
                UserAdminController.class.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void kitchenCanReadOrdersAndUsesDedicatedPreparationPermission() throws Exception {
        Method orders = CommercialOperationsAdminController.class.getMethod("orders");
        assertEquals("hasAuthority('PERM_ORDERS_READ')",
                orders.getAnnotation(PreAuthorize.class).value());

        Method preparation = CommercialOperationsAdminController.class.getMethod(
                "updateOrderPreparationStatus",
                UUID.class,
                CommercialOperationsAdminController.OrderStatusRequest.class);
        assertEquals("hasAuthority('PERM_ORDERS_PREPARE')",
                preparation.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void customerAgendaInventoryAnalyticsAndConversationsHaveIndependentPermissions() {
        assertEquals("hasAuthority('PERM_CUSTOMERS_READ')",
                CustomerController.class.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('PERM_BOOKINGS_READ')",
                BookingController.class.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('PERM_ANALYTICS_READ')",
                SalesAnalyticsController.class.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('PERM_CONVERSATIONS_READ')",
                MessagingConversationQueryController.class.getAnnotation(PreAuthorize.class).value());

        try {
            Method list = InventoryController.class.getMethod("list");
            assertEquals("hasAuthority('PERM_INVENTORY_READ')",
                    list.getAnnotation(PreAuthorize.class).value());
        } catch (ReflectiveOperationException e) {
            fail(e);
        }
    }
}
