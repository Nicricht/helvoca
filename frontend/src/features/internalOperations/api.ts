import { apiRequest } from "../../api/client";

export type PilotReadinessCheck = {
  code: string;
  label: string;
  ready: boolean;
  detail?: string | null;
};

export type PilotReadiness = {
  ready: boolean;
  passed: number;
  total: number;
  blockers?: string[];
  checks?: PilotReadinessCheck[];
};

export type PilotControl = {
  status: string;
  launchDecision?: string | null;
  blockers?: string[];
  responsibleName?: string | null;
  responsibleContact?: string | null;
  goal?: string | null;
  plannedEndAt?: string | null;
  canStart?: boolean;
  canPause?: boolean;
  canResume?: boolean;
  canComplete?: boolean;
};

export type PilotControlConfiguration = {
  responsibleName: string;
  responsibleContact: string;
  goal: string;
  plannedEndAt: string | null;
};

export type PilotPreflightCheck = {
  code: string;
  label: string;
  passed: boolean;
  required?: boolean;
  detail?: string | null;
};

export type PilotPreflightSnapshot = {
  ordersToday?: number;
  paymentAttemptsToday?: number;
  successfulPaymentsToday?: number;
  availableInventoryUnits?: number;
  lowStockAlerts?: number;
  outOfStockAlerts?: number;
  reconciliationAnomalies?: number;
};

export type PilotPreflight = {
  decision: string;
  pilotStatus?: string | null;
  trafficMode?: string | null;
  globalExternalEffectsEnabled?: boolean;
  blockers?: string[];
  warnings?: string[];
  checks?: PilotPreflightCheck[];
  snapshot?: PilotPreflightSnapshot;
};

export type PilotMetricPeriod = {
  calls?: number;
  whatsappConversations?: number;
  bookings?: number;
  orders?: number;
  successfulPayments?: number;
  pendingPayments?: number;
  failedPayments?: number;
  humanTransfers?: number;
  callFailures?: number;
  confirmedRevenueByCurrency?: Record<string, number>;
  paidOrderConversionPct?: number;
  paymentSuccessRatePct?: number;
  callFailureRatePct?: number;
  humanTransferRatePct?: number;
};

export type PilotMetrics = {
  timezone?: string;
  today?: PilotMetricPeriod;
  last7Days?: PilotMetricPeriod;
};

export function getPilotReadiness() {
  return apiRequest<PilotReadiness>("/api/v1/operations/pilot-readiness");
}

export function getPilotControl() {
  return apiRequest<PilotControl>("/api/v1/operations/pilot-control");
}

export function configurePilotControl(configuration: PilotControlConfiguration) {
  return apiRequest<PilotControl>("/api/v1/operations/pilot-control", {
    method: "PUT",
    body: JSON.stringify(configuration)
  });
}

export function transitionPilotControl(action: "start" | "pause" | "resume" | "complete") {
  return apiRequest<PilotControl>(\`/api/v1/operations/pilot-control/\${action}\`, {
    method: "POST"
  });
}

export function getPilotPreflight() {
  return apiRequest<PilotPreflight>("/api/v1/operations/pilot-preflight");
}

export function getPilotMetrics() {
  return apiRequest<PilotMetrics>("/api/v1/operations/pilot-metrics");
}
