package cl.helvoca.security;

import java.util.EnumSet;
import java.util.Set;

public enum PermissionCode {
    BUSINESS_READ,
    BUSINESS_CONFIGURE,
    TEAM_READ,
    TEAM_MANAGE,
    BILLING_READ,
    BILLING_MANAGE,
    CUSTOMERS_READ,
    CUSTOMERS_MANAGE,
    CUSTOMERS_EXPORT,
    CONVERSATIONS_READ,
    CONVERSATIONS_RESPOND,
    BOOKINGS_READ,
    BOOKINGS_MANAGE,
    ORDERS_READ,
    ORDERS_MANAGE,
    ORDERS_PREPARE,
    DELIVERIES_READ,
    DELIVERIES_MANAGE,
    QUOTES_READ,
    QUOTES_MANAGE,
    LEADS_READ,
    LEADS_MANAGE,
    INVENTORY_READ,
    INVENTORY_MANAGE,
    CATALOG_READ,
    CATALOG_MANAGE,
    ANALYTICS_READ,
    OPERATIONS_READ,
    OPERATIONS_MANAGE,
    CHANNELS_READ,
    CHANNELS_MANAGE,
    AI_READ,
    AI_MANAGE,
    REQUESTS_READ,
    REQUESTS_MANAGE;

    private static final Set<PermissionCode> BUSINESS =
            Set.copyOf(EnumSet.allOf(PermissionCode.class));

    public static Set<PermissionCode> businessPermissions() {
        return BUSINESS;
    }
}
