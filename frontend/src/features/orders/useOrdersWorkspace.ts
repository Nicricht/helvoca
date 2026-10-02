import { useQuery } from "@tanstack/react-query";
import { getCurrentUser, getDeliveries, getOrders } from "./api";

const MANAGE_ROLES = new Set([
  "BUSINESS_OWNER",
  "BUSINESS_ADMIN",
  "MANAGER",
  "RECEPTION",
  "SALES",
  "OPERATOR"
]);

const PREPARE_ROLES = new Set([
  "BUSINESS_OWNER",
  "BUSINESS_ADMIN",
  "MANAGER",
  "KITCHEN",
  "OPERATOR"
]);

const CONVERSATION_ROLES = new Set([
  "BUSINESS_OWNER",
  "BUSINESS_ADMIN",
  "MANAGER",
  "RECEPTION",
  "SALES",
  "OPERATOR"
]);

const queryDefaults = {
  retry: false,
  refetchOnWindowFocus: false
} as const;

export function useOrdersWorkspace() {
  const me = useQuery({
    queryKey: ["auth", "me"],
    queryFn: getCurrentUser,
    ...queryDefaults
  });

  const orders = useQuery({
    queryKey: ["commercial", "orders"],
    queryFn: getOrders,
    ...queryDefaults
  });

  const deliveries = useQuery({
    queryKey: ["commercial", "deliveries"],
    queryFn: getDeliveries,
    ...queryDefaults
  });

  const roles = me.data?.roles ?? [];
  const permissions = me.data?.permissions;
  const hasPermissionClaims = Array.isArray(permissions);

  const canManage = hasPermissionClaims
    ? permissions.includes("ORDERS_MANAGE")
    : roles.some(role => MANAGE_ROLES.has(role));

  const canPrepare = hasPermissionClaims
    ? permissions.includes("ORDERS_PREPARE")
    : roles.some(role => PREPARE_ROLES.has(role));

  const canReadConversations = hasPermissionClaims
    ? permissions.includes("CONVERSATIONS_READ")
    : roles.some(role => CONVERSATION_ROLES.has(role));

  const canReadDeliveries = hasPermissionClaims
    ? permissions.includes("DELIVERIES_READ")
    : roles.some(role => MANAGE_ROLES.has(role) || role === "DISPATCH");

  async function refetchOrders() {
    await orders.refetch();
  }

  return {
    me,
    orders,
    deliveries,
    canManage,
    canPrepare,
    canReadConversations,
    canReadDeliveries,
    refetchOrders
  };
}
