package cl.helvoca.security;

import cl.helvoca.user.RoleCode;

import java.util.EnumSet;
import java.util.Set;

import static cl.helvoca.security.PermissionCode.*;

public final class RolePermissionCatalog {
    private RolePermissionCatalog() {}

    public static Set<PermissionCode> permissionsFor(Set<RoleCode> roles) {
        EnumSet<PermissionCode> permissions = EnumSet.noneOf(PermissionCode.class);
        if (roles == null) return Set.of();
        for (RoleCode role : roles) {
            if (role != null) permissions.addAll(permissionsFor(role));
        }
        return Set.copyOf(permissions);
    }

    public static Set<PermissionCode> permissionsFor(RoleCode role) {
        if (role == null || role == RoleCode.PLATFORM_ADMIN) return Set.of();

        return switch (role) {
            case BUSINESS_OWNER, BUSINESS_ADMIN -> PermissionCode.businessPermissions();
            case MANAGER -> set(
                    BUSINESS_READ,
                    CUSTOMERS_READ, CUSTOMERS_MANAGE,
                    CONVERSATIONS_READ, CONVERSATIONS_RESPOND,
                    BOOKINGS_READ, BOOKINGS_MANAGE,
                    ORDERS_READ, ORDERS_MANAGE, ORDERS_PREPARE,
                    DELIVERIES_READ, DELIVERIES_MANAGE,
                    QUOTES_READ, QUOTES_MANAGE,
                    LEADS_READ, LEADS_MANAGE,
                    INVENTORY_READ, INVENTORY_MANAGE,
                    CATALOG_READ, CATALOG_MANAGE,
                    ANALYTICS_READ,
                    OPERATIONS_READ, OPERATIONS_MANAGE,
                    CHANNELS_READ,
                    AI_READ,
                    REQUESTS_READ, REQUESTS_MANAGE);
            case RECEPTION -> set(
                    BUSINESS_READ,
                    CUSTOMERS_READ, CUSTOMERS_MANAGE,
                    CONVERSATIONS_READ, CONVERSATIONS_RESPOND,
                    BOOKINGS_READ, BOOKINGS_MANAGE,
                    ORDERS_READ, ORDERS_MANAGE,
                    QUOTES_READ,
                    LEADS_READ, LEADS_MANAGE,
                    CATALOG_READ,
                    REQUESTS_READ, REQUESTS_MANAGE);
            case STAFF -> set(
                    BUSINESS_READ,
                    CUSTOMERS_READ,
                    CONVERSATIONS_READ,
                    BOOKINGS_READ,
                    ORDERS_READ,
                    INVENTORY_READ,
                    CATALOG_READ,
                    REQUESTS_READ);
            case KITCHEN -> set(
                    BUSINESS_READ,
                    ORDERS_READ, ORDERS_PREPARE,
                    CATALOG_READ);
            case DISPATCH -> set(
                    BUSINESS_READ,
                    CUSTOMERS_READ,
                    ORDERS_READ,
                    DELIVERIES_READ, DELIVERIES_MANAGE);
            case PROFESSIONAL -> set(
                    BUSINESS_READ,
                    CUSTOMERS_READ,
                    CONVERSATIONS_READ,
                    BOOKINGS_READ, BOOKINGS_MANAGE,
                    CATALOG_READ,
                    REQUESTS_READ);
            case WAREHOUSE -> set(
                    BUSINESS_READ,
                    INVENTORY_READ, INVENTORY_MANAGE,
                    CATALOG_READ);
            case SALES -> set(
                    BUSINESS_READ,
                    CUSTOMERS_READ, CUSTOMERS_MANAGE,
                    CONVERSATIONS_READ, CONVERSATIONS_RESPOND,
                    ORDERS_READ, ORDERS_MANAGE,
                    QUOTES_READ, QUOTES_MANAGE,
                    LEADS_READ, LEADS_MANAGE,
                    CATALOG_READ,
                    ANALYTICS_READ,
                    REQUESTS_READ, REQUESTS_MANAGE);
            case OPERATOR -> set(
                    BUSINESS_READ,
                    CUSTOMERS_READ, CUSTOMERS_MANAGE,
                    CONVERSATIONS_READ, CONVERSATIONS_RESPOND,
                    BOOKINGS_READ, BOOKINGS_MANAGE,
                    ORDERS_READ, ORDERS_MANAGE, ORDERS_PREPARE,
                    DELIVERIES_READ,
                    QUOTES_READ,
                    LEADS_READ,
                    INVENTORY_READ,
                    CATALOG_READ,
                    ANALYTICS_READ,
                    OPERATIONS_READ,
                    CHANNELS_READ,
                    AI_READ,
                    REQUESTS_READ, REQUESTS_MANAGE);
            case PLATFORM_ADMIN -> Set.of();
        };
    }

    private static Set<PermissionCode> set(PermissionCode first, PermissionCode... rest) {
        EnumSet<PermissionCode> result = EnumSet.of(first, rest);
        return Set.copyOf(result);
    }
}
