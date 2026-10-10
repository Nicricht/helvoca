import { apiRequest } from "../../api/client";

export type ImportItemKind = "PRODUCT" | "SERVICE";

export interface BusinessImportProposal {
  name: string;
  description?: string | null;
  price?: number | null;
  currency?: string | null;
  sku?: string | null;
  onHand?: number | null;
  category?: string | null;
  kind?: ImportItemKind | null;
  durationMinutes?: number | null;
  confidence?: number | null;
  sourceName?: string | null;
}

export interface BusinessImportSource {
  name: string;
  kind?: string | null;
  rowCount?: number | null;
  method?: string | null;
  recognized?: boolean;
  warnings?: string[];
}

export interface BusinessImportPreview {
  businessName: string;
  products: BusinessImportProposal[];
  sources: BusinessImportSource[];
  warnings: string[];
  aiUsed: boolean;
}

export interface BusinessImportApplyItem {
  name: string;
  description: string | null;
  price: number | null;
  currency: string;
  sku: string | null;
  onHand: number | null;
  category: string | null;
  kind: ImportItemKind;
  durationMinutes: number | null;
  sourceName: string | null;
}

export interface BusinessImportApplyResultItem {
  catalogItemId: string;
  name: string;
  action: string;
  inventoryConfigured: boolean;
}

export interface BusinessImportApplyResult {
  created: number;
  updated: number;
  inventoryConfigured: number;
  items: BusinessImportApplyResultItem[];
}

export function previewBusinessImport(businessName: string, files: File[]) {
  const form = new FormData();
  form.append("businessName", businessName);
  files.forEach(file => form.append("files", file, file.name));
  return apiRequest<BusinessImportPreview>("/api/v1/onboarding/import/preview", {
    method: "POST",
    body: form
  });
}

export function applyBusinessImport(products: BusinessImportApplyItem[]) {
  return apiRequest<BusinessImportApplyResult>("/api/v1/onboarding/import/apply", {
    method: "POST",
    body: JSON.stringify({ products })
  });
}

export interface BusinessImportAiQuota {
  status: "AVAILABLE" | "DISABLED" | "BUSINESS_INACTIVE" | "SUBSCRIPTION_INACTIVE"
    | "PERIOD_EXPIRED" | "NOT_INCLUDED" | "LIMIT_REACHED" | "UNAVAILABLE";
  planCode: string | null;
  limit: number;
  used: number;
  remaining: number;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  masterEnabled: boolean;
}

export function getBusinessImportAiQuota() {
  return apiRequest<BusinessImportAiQuota>("/api/v1/onboarding/import/ai-quota");
}
