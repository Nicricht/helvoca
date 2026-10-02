import { apiRequest } from "../../api/client";

export interface CurrentUser {
  email?: string;
  roles?: string[];
}

export interface BusinessSummary {
  id?: string;
  name?: string;
}

export interface CatalogItem {
  id: string;
  kind?: string;
  name?: string;
  description?: string;
  price?: number | null;
  currency?: string;
  active?: boolean;
}

export interface InventoryStock {
  id?: string;
  catalogItemId: string;
  sku?: string | null;
  trackingEnabled?: boolean;
  onHand?: number | null;
  reserved?: number | null;
  available?: number | null;
  reorderThreshold?: number | null;
  lowStock?: boolean;
}

export interface InventoryAlert {
  id: string;
  catalogItemId?: string;
  type?: string;
  acknowledged?: boolean;
  subjectName?: string;
  available?: number;
  reorderThreshold?: number;
}

export interface RestockSubscription {
  id: string;
  catalogItemId?: string;
  preferredChannel?: string;
  contact?: string;
}

export interface RestockNotification {
  id: string;
  catalogItemId?: string;
  preferredChannel?: string;
  contact?: string;
  available?: number;
}

export function getCurrentUser() {
  return apiRequest<CurrentUser>("/api/v1/auth/me");
}

export function getBusiness() {
  return apiRequest<BusinessSummary>("/api/v1/business");
}

export function getCatalog() {
  return apiRequest<CatalogItem[]>("/api/v1/catalog");
}

export function getInventory() {
  return apiRequest<InventoryStock[]>("/api/v1/inventory");
}

export function getInventoryAlerts() {
  return apiRequest<InventoryAlert[]>("/api/v1/inventory/alerts");
}

export function getRestockSubscriptions() {
  return apiRequest<RestockSubscription[]>("/api/v1/inventory/restock-subscriptions");
}

export function getRestockNotifications() {
  return apiRequest<RestockNotification[]>("/api/v1/inventory/restock-subscriptions/notifications");
}
