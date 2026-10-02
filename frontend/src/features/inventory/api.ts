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

export interface CreateCatalogProductInput {
  kind: "PRODUCT";
  name: string;
  description: string | null;
  price: number;
  currency: string;
  durationMinutes: null;
  metadataJson: null;
  active: true;
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


export interface ConfigureInventoryStockInput {
  sku: string | null;
  trackingEnabled: boolean;
  onHand: number;
  reorderThreshold: number;
  note: string | null;
}

export interface AdjustInventoryStockInput {
  delta: number;
  referenceType: "MANUAL";
  referenceId: string | null;
  note: string | null;
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


export function configureInventoryStock(
  catalogItemId: string,
  input: ConfigureInventoryStockInput
) {
  return apiRequest<InventoryStock>(
    `/api/v1/inventory/${encodeURIComponent(catalogItemId)}`,
    {
      method: "PUT",
      body: JSON.stringify(input)
    }
  );
}

export function adjustInventoryStock(
  catalogItemId: string,
  input: AdjustInventoryStockInput
) {
  return apiRequest<InventoryStock>(
    `/api/v1/inventory/${encodeURIComponent(catalogItemId)}/adjustments`,
    {
      method: "POST",
      body: JSON.stringify(input)
    }
  );
}


export function createCatalogProduct(input: CreateCatalogProductInput) {
  return apiRequest<CatalogItem>("/api/v1/catalog", {
    method: "POST",
    body: JSON.stringify(input)
  });
}
