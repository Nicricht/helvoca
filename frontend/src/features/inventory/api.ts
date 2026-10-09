import { apiRequest } from "../../api/client";

export interface CurrentUser {
  email?: string;
  roles?: string[];
  permissions?: string[];
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
  durationMinutes?: number | null;
  metadataJson?: string | null;
  active?: boolean;
}

export interface CatalogMedia {
  id: string;
  catalogItemId: string;
  mediaType: "IMAGE" | "VIDEO" | "DOCUMENT";
  mediaUrl: string;
  mimeType?: string | null;
  caption?: string | null;
  sortOrder: number;
  active: boolean;
}

export function getCatalogMedia(catalogItemId: string) {
  return apiRequest<CatalogMedia[]>(
    `/api/v1/catalog/${encodeURIComponent(catalogItemId)}/media`
  );
}

export interface CreateCatalogProductInput {
  kind: "PRODUCT";
  name: string;
  description: string | null;
  price: number;
  currency: string;
  durationMinutes: number | null;
  metadataJson: string | null;
  active: boolean;
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

export interface InventoryMovement {
  id: string;
  type: "CONFIGURE" | "ADJUSTMENT" | "RESERVATION" | "RELEASE" | "CONSUMPTION";
  quantityDelta: number;
  reservedDelta: number;
  onHandAfter: number;
  reservedAfter: number;
  referenceType?: string | null;
  referenceId?: string | null;
  note?: string | null;
  createdAt: string;
}

export interface InventoryVariant {
  id: string;
  catalogItemId: string;
  name: string;
  optionValuesJson: string;
  sku: string;
  trackingEnabled: boolean;
  onHand: number;
  reserved: number;
  available: number;
  reorderThreshold: number;
  lowStock: boolean;
  active: boolean;
}

export interface InventoryVariantInput {
  name: string;
  optionValuesJson: string;
  sku: string;
  trackingEnabled: boolean;
  onHand: number;
  reorderThreshold: number;
  active: boolean;
  note: string | null;
}

export interface InventoryVariantAdjustmentInput {
  delta: number;
  note: string | null;
}

export interface InventoryVariantMovement {
  id: string;
  type: "CONFIGURE" | "ADJUSTMENT" | "RESERVATION" | "RELEASE" | "CONSUMPTION";
  quantityDelta: number;
  reservedDelta: number;
  onHandAfter: number;
  reservedAfter: number;
  note?: string | null;
  createdAt: string;
}

export interface InventoryAlert {
  id: string;
  catalogItemId?: string;
  variantId?: string | null;
  type?: string;
  acknowledged?: boolean;
  acknowledgedAt?: string | null;
  subjectName?: string;
  sku?: string | null;
  available?: number;
  reorderThreshold?: number;
  createdAt?: string;
}

export interface RestockSubscription {
  id: string;
  customerId?: string | null;
  catalogItemId?: string;
  variantId?: string | null;
  preferredChannel?: string;
  contact?: string;
  status?: string;
  createdAt?: string;
}

export interface RestockNotification {
  id: string;
  subscriptionId?: string;
  customerId?: string | null;
  catalogItemId?: string;
  variantId?: string | null;
  preferredChannel?: string;
  contact?: string;
  subjectName?: string;
  sku?: string | null;
  available?: number;
  status?: string;
  createdAt?: string;
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

export function updateCatalogProduct(
  catalogItemId: string,
  input: CreateCatalogProductInput
) {
  return apiRequest<CatalogItem>(
    `/api/v1/catalog/${encodeURIComponent(catalogItemId)}`,
    {
      method: "PUT",
      body: JSON.stringify(input)
    }
  );
}


export function getInventoryHistory(catalogItemId: string) {
  return apiRequest<InventoryMovement[]>(
    `/api/v1/inventory/${encodeURIComponent(catalogItemId)}/movements`
  );
}


export function getInventoryVariants(catalogItemId: string) {
  return apiRequest<InventoryVariant[]>(
    `/api/v1/inventory/${encodeURIComponent(catalogItemId)}/variants`
  );
}

export function createInventoryVariant(
  catalogItemId: string,
  input: InventoryVariantInput
) {
  return apiRequest<InventoryVariant>(
    `/api/v1/inventory/${encodeURIComponent(catalogItemId)}/variants`,
    { method: "POST", body: JSON.stringify(input) }
  );
}

export function updateInventoryVariant(
  catalogItemId: string,
  variantId: string,
  input: InventoryVariantInput
) {
  return apiRequest<InventoryVariant>(
    `/api/v1/inventory/${encodeURIComponent(catalogItemId)}/variants/${encodeURIComponent(variantId)}`,
    { method: "PUT", body: JSON.stringify(input) }
  );
}

export function adjustInventoryVariant(
  catalogItemId: string,
  variantId: string,
  input: InventoryVariantAdjustmentInput
) {
  return apiRequest<InventoryVariant>(
    `/api/v1/inventory/${encodeURIComponent(catalogItemId)}/variants/${encodeURIComponent(variantId)}/adjustments`,
    { method: "POST", body: JSON.stringify(input) }
  );
}

export function deactivateInventoryVariant(catalogItemId: string, variantId: string) {
  return apiRequest<InventoryVariant>(
    `/api/v1/inventory/${encodeURIComponent(catalogItemId)}/variants/${encodeURIComponent(variantId)}/deactivate`,
    { method: "POST" }
  );
}

export function getInventoryVariantHistory(catalogItemId: string, variantId: string) {
  return apiRequest<InventoryVariantMovement[]>(
    `/api/v1/inventory/${encodeURIComponent(catalogItemId)}/variants/${encodeURIComponent(variantId)}/movements`
  );
}


export function acknowledgeInventoryAlert(alertId: string) {
  return apiRequest<InventoryAlert>(
    `/api/v1/inventory/alerts/${encodeURIComponent(alertId)}/acknowledge`,
    { method: "POST" }
  );
}

export function cancelRestockSubscription(subscriptionId: string) {
  return apiRequest<RestockSubscription>(
    `/api/v1/inventory/restock-subscriptions/${encodeURIComponent(subscriptionId)}/cancel`,
    { method: "POST" }
  );
}
