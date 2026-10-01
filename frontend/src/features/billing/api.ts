import { apiRequest } from "../../api/client";

export type RoleCode = string;

export interface CurrentUser {
  email?: string;
  roles?: RoleCode[];
}

export interface Subscription {
  businessId?: string;
  plan?: string;
  publicPlanCode?: string;
  planName?: string;
  status?: string;
  serviceAllowed?: boolean;
  maxConcurrentCalls?: number;
  includedMinutes?: number;
  usedMinutes?: number;
  overageMinutes?: number;
  currentPeriodStart?: string;
  currentPeriodEnd?: string;
  graceUntil?: string | null;
  billingProviderConnected?: boolean;
  entitlements?: string[];
  legacyFallback?: boolean;
}

export interface UsageItem {
  meterKey?: string;
  unit?: string;
  quantity?: number;
  eventCount?: number;
}

export interface UsageStatus {
  includedMinutes?: number;
  usedMinutes?: number;
  overageMinutes?: number;
  usagePercent?: number;
  alertLevel?: string;
  estimatedOverageChargeClp?: number;
  safetyLimitMinutes?: number;
  safetyRemainingMinutes?: number;
  safetyExceeded?: boolean;
}

export function getCurrentUser() {
  return apiRequest<CurrentUser>("/api/v1/auth/me");
}

export function getSubscription() {
  return apiRequest<Subscription>("/api/v1/subscription");
}

export function getUsageSummary(from: string, to: string) {
  const query = new URLSearchParams({ from, to });
  return apiRequest<UsageItem[]>(`/api/v1/usage/summary?${query.toString()}`);
}

export function getUsageStatus() {
  return apiRequest<UsageStatus>("/api/v1/usage/status");
}
