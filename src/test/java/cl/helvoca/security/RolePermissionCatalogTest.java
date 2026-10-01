package cl.helvoca.security;

import cl.helvoca.user.RoleCode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RolePermissionCatalogTest {

    @Test
    void ownerHasEveryBusinessPermission() {
        assertEquals(
                Set.copyOf(PermissionCode.businessPermissions()),
                RolePermissionCatalog.permissionsFor(Set.of(RoleCode.BUSINESS_OWNER)));
    }

    @Test
    void kitchenCanWorkOrdersWithoutSeeingCustomersBillingOrTeam() {
        var permissions = RolePermissionCatalog.permissionsFor(Set.of(RoleCode.KITCHEN));

        assertTrue(permissions.contains(PermissionCode.ORDERS_READ));
        assertTrue(permissions.contains(PermissionCode.ORDERS_PREPARE));
        assertFalse(permissions.contains(PermissionCode.CUSTOMERS_READ));
        assertFalse(permissions.contains(PermissionCode.BILLING_READ));
        assertFalse(permissions.contains(PermissionCode.TEAM_MANAGE));
    }

    @Test
    void receptionCanHandleCustomerFacingWorkWithoutAdministrativePower() {
        var permissions = RolePermissionCatalog.permissionsFor(Set.of(RoleCode.RECEPTION));

        assertTrue(permissions.containsAll(Set.of(
                PermissionCode.CUSTOMERS_READ,
                PermissionCode.CUSTOMERS_MANAGE,
                PermissionCode.CONVERSATIONS_READ,
                PermissionCode.CONVERSATIONS_RESPOND,
                PermissionCode.BOOKINGS_READ,
                PermissionCode.BOOKINGS_MANAGE,
                PermissionCode.ORDERS_READ,
                PermissionCode.ORDERS_MANAGE)));
        assertFalse(permissions.contains(PermissionCode.BILLING_MANAGE));
        assertFalse(permissions.contains(PermissionCode.TEAM_MANAGE));
        assertFalse(permissions.contains(PermissionCode.BUSINESS_CONFIGURE));
    }

    @Test
    void warehouseAndProfessionalAreCrossVerticalRatherThanRestaurantSpecific() {
        var warehouse = RolePermissionCatalog.permissionsFor(Set.of(RoleCode.WAREHOUSE));
        assertTrue(warehouse.containsAll(Set.of(
                PermissionCode.INVENTORY_READ,
                PermissionCode.INVENTORY_MANAGE,
                PermissionCode.CATALOG_READ)));
        assertFalse(warehouse.contains(PermissionCode.BOOKINGS_MANAGE));

        var professional = RolePermissionCatalog.permissionsFor(Set.of(RoleCode.PROFESSIONAL));
        assertTrue(professional.containsAll(Set.of(
                PermissionCode.BOOKINGS_READ,
                PermissionCode.BOOKINGS_MANAGE,
                PermissionCode.CUSTOMERS_READ)));
        assertFalse(professional.contains(PermissionCode.INVENTORY_MANAGE));
    }

    @Test
    void legacyOperatorKeepsBroadOperationalCompatibility() {
        var permissions = RolePermissionCatalog.permissionsFor(Set.of(RoleCode.OPERATOR));

        assertTrue(permissions.containsAll(Set.of(
                PermissionCode.CUSTOMERS_READ,
                PermissionCode.CONVERSATIONS_READ,
                PermissionCode.BOOKINGS_READ,
                PermissionCode.ORDERS_READ,
                PermissionCode.INVENTORY_READ,
                PermissionCode.ANALYTICS_READ)));
        assertFalse(permissions.contains(PermissionCode.TEAM_MANAGE));
        assertFalse(permissions.contains(PermissionCode.BILLING_MANAGE));
    }

    @Test
    void platformAdminDoesNotReceiveTenantBusinessPermissions() {
        assertTrue(RolePermissionCatalog.permissionsFor(Set.of(RoleCode.PLATFORM_ADMIN)).isEmpty());
    }

    @Test
    void remainingReusableRolesHaveExpectedBoundaries() {
        var admin = RolePermissionCatalog.permissionsFor(Set.of(RoleCode.BUSINESS_ADMIN));
        assertEquals(PermissionCode.businessPermissions(), admin);

        var manager = RolePermissionCatalog.permissionsFor(Set.of(RoleCode.MANAGER));
        assertTrue(manager.contains(PermissionCode.OPERATIONS_MANAGE));
        assertFalse(manager.contains(PermissionCode.TEAM_MANAGE));
        assertFalse(manager.contains(PermissionCode.BILLING_MANAGE));

        var staff = RolePermissionCatalog.permissionsFor(Set.of(RoleCode.STAFF));
        assertTrue(staff.contains(PermissionCode.ORDERS_READ));
        assertFalse(staff.contains(PermissionCode.ORDERS_MANAGE));

        var dispatch = RolePermissionCatalog.permissionsFor(Set.of(RoleCode.DISPATCH));
        assertTrue(dispatch.contains(PermissionCode.DELIVERIES_MANAGE));
        assertFalse(dispatch.contains(PermissionCode.INVENTORY_MANAGE));

        var sales = RolePermissionCatalog.permissionsFor(Set.of(RoleCode.SALES));
        assertTrue(sales.contains(PermissionCode.LEADS_MANAGE));
        assertTrue(sales.contains(PermissionCode.QUOTES_MANAGE));
        assertFalse(sales.contains(PermissionCode.BILLING_MANAGE));
    }

    @Test
    void nullInputsNeverGainPermissions() {
        assertTrue(RolePermissionCatalog.permissionsFor((Set<RoleCode>) null).isEmpty());
        assertTrue(RolePermissionCatalog.permissionsFor((RoleCode) null).isEmpty());
        assertTrue(RolePermissionCatalog.permissionsFor(new java.util.HashSet<>(java.util.Arrays.asList(RoleCode.STAFF, null)))
                .contains(PermissionCode.ORDERS_READ));
    }

}
