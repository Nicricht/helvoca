import { useQuery } from "@tanstack/react-query";
import {
  getBusiness,
  getCatalog,
  getCurrentUser,
  getInventory,
  getInventoryAlerts,
  getRestockNotifications,
  getRestockSubscriptions
} from "./api";

const MANAGE_ROLES = new Set(["BUSINESS_ADMIN", "BUSINESS_OWNER"]);

const queryDefaults = {
  retry: false,
  refetchOnWindowFocus: false
} as const;

export function useInventoryWorkspace() {
  const me = useQuery({
    queryKey: ["auth", "me"],
    queryFn: getCurrentUser,
    ...queryDefaults
  });

  const business = useQuery({
    queryKey: ["business", "summary"],
    queryFn: getBusiness,
    ...queryDefaults
  });

  const catalog = useQuery({
    queryKey: ["catalog", "inventory-products"],
    queryFn: getCatalog,
    ...queryDefaults
  });

  const inventory = useQuery({
    queryKey: ["inventory", "stock"],
    queryFn: getInventory,
    ...queryDefaults
  });

  const alerts = useQuery({
    queryKey: ["inventory", "alerts"],
    queryFn: getInventoryAlerts,
    ...queryDefaults
  });

  const restockSubscriptions = useQuery({
    queryKey: ["inventory", "restock-subscriptions"],
    queryFn: getRestockSubscriptions,
    ...queryDefaults
  });

  const restockNotifications = useQuery({
    queryKey: ["inventory", "restock-notifications"],
    queryFn: getRestockNotifications,
    ...queryDefaults
  });

  const roles = me.data?.roles ?? [];
  const canManage = roles.some(role => MANAGE_ROLES.has(role));

  async function refetchPrimary() {
    await Promise.all([
      me.refetch(),
      business.refetch(),
      catalog.refetch(),
      inventory.refetch(),
      alerts.refetch(),
      restockSubscriptions.refetch(),
      restockNotifications.refetch()
    ]);
  }

  return {
    me,
    business,
    catalog,
    inventory,
    alerts,
    restockSubscriptions,
    restockNotifications,
    canManage,
    refetchPrimary
  };
}
