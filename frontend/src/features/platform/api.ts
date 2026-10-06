import { apiRequest } from "../../api/client";

export type ReadinessItem = {
  state: string;
  detail: string;
};

export type PlatformDemoReadiness = {
  runtimeConfigured: boolean;
  runtimeBusinessId: string | null;
  runtime: ReadinessItem;
  voiceNumber: ReadinessItem;
  voiceAi: ReadinessItem;
  businessData: ReadinessItem;
  operations: ReadinessItem;
  whatsapp: ReadinessItem;
  payment: ReadinessItem;
  externalEffects: ReadinessItem;
};

export type PlatformDemoProfileRequest = {
  displayName: string;
  businessName: string;
  timezone: string;
  language: string;
  catalog: Record<string, unknown>;
  hours: Record<string, unknown>;
  knowledge: Record<string, unknown>;
  greeting: string;
  instructions: string | null;
  capabilities: string[];
  presenterNotes: string | null;
  sourceMetadata: Record<string, unknown>;
};

export type PlatformDemoProfile = PlatformDemoProfileRequest & {
  id: string;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type PlatformDemoSession = {
  id: string;
  correlationId: string;
  demoProfileId: string;
  runtimeBusinessId: string;
  state: string;
  configurationRevision?: string | null;
  stagedAt?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  failureReason?: string | null;
  readiness?: PlatformDemoReadiness | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  operator?: string | null;
  configurationSnapshot?: Record<string, unknown> | null;
  readinessSnapshot?: Record<string, unknown> | null;
  externalEffectsState?: string | null;
  paymentState?: string | null;
  convertedPilotBusinessId?: string | null;
};

export type TimelineEvent = {
  at: string;
  type: string;
  entityId?: string | null;
  status: string;
  detail: string;
};

export type ProofOfValue = {
  state: string;
  calls: number;
  conversations: number;
  operations: number;
  facts: string[];
  followUps: string[];
};

export type PlatformDemoTimeline = {
  sessionId: string | null;
  runtimeBusinessId: string | null;
  sessionStatus: string;
  events: TimelineEvent[];
  proofOfValue: ProofOfValue;
};

export type PlatformDemoConversion = {
  sessionId: string;
  pilotBusinessId: string;
  pilotBusinessName: string;
  mode: string;
  invitationId?: string | null;
  invitationStatus?: string | null;
  invitationExpiresAt?: string | null;
  invitePath?: string | null;
  onboardingPath?: string | null;
  idempotentReplay: boolean;
};

export type PlatformBusinessProvisioningRequest = {
  businessName: string;
  timezone: string;
  language: string;
  humanTransferPhone: string | null;
  adminName: string;
  adminEmail: string;
};

export type PlatformBusinessProvisioningResponse = PlatformBusinessProvisioningRequest & {
  businessId: string;
  invitationId: string;
  invitationStatus: string;
  invitationExpiresAt: string;
  invitePath: string;
  onboardingPath: string;
};

export type PlatformBusinessEconomics = {
  businessId: string;
  businessName: string;
  planCode: string;
  planName: string;
  status: string;
  includedMinutes: number;
  usedMinutes: number;
  usageAlertLevel: string;
  basePlanValueClp: number;
  estimatedOverageValueClp: number;
  estimatedCommercialValueClp: number | null;
  activeVoiceNumberCount?: number;
  estimatedFixedPhoneCostUsd?: number | null;
  estimatedPlatformCostUsd: number;
  estimatedPlatformCostClp: number | null;
  estimatedGrossMarginClp: number | null;
  estimatedGrossMarginPercent: number | null;
  commercialValueUnknown: boolean;
};

export type PlatformProviderEconomics = {
  aiProvider: string;
  aiModel: string;
  callCount: number;
  minutes: number;
  estimatedTelephonyCostUsd: number;
  estimatedAiCostUsd: number;
  estimatedTotalCostUsd: number;
  estimatedTotalCostClp: number | null;
};

export type PlatformEconomics = {
  businessCount?: number;
  basePlanValueClp?: number;
  estimatedOverageValueClp?: number;
  estimatedCommercialValueClp?: number;
  estimatedPlatformCostUsd?: number;
  estimatedPlatformCostClp?: number | null;
  estimatedGrossMarginClp?: number | null;
  estimatedGrossMarginPercent?: number | null;
  usdToClpRate?: number | null;
  businessesWithUnknownCommercialValue?: number;
  businesses?: PlatformBusinessEconomics[];
  providers?: PlatformProviderEconomics[];
};

export function getPlatformDemoReadiness() {
  return apiRequest<PlatformDemoReadiness>("/api/v1/platform/demos/readiness");
}

export function listPlatformDemoProfiles() {
  return apiRequest<PlatformDemoProfile[]>("/api/v1/platform/demos");
}

export function createPlatformDemoProfile(payload: PlatformDemoProfileRequest) {
  return apiRequest<PlatformDemoProfile>("/api/v1/platform/demos", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function preparePlatformDemo(profileId: string) {
  return apiRequest<PlatformDemoSession>(
    "/api/v1/platform/demos/" + encodeURIComponent(profileId) + "/prepare",
    { method: "POST" }
  );
}

export function getCurrentPlatformDemoSession() {
  return apiRequest<PlatformDemoSession | null>("/api/v1/platform/demo-sessions/current");
}

export function transitionPlatformDemo(sessionId: string, action: "start" | "finish" | "abort") {
  return apiRequest<PlatformDemoSession>(
    "/api/v1/platform/demo-sessions/" + encodeURIComponent(sessionId) + "/" + action,
    { method: "POST" }
  );
}

export function getPlatformDemoTimeline(sessionId: string) {
  return apiRequest<PlatformDemoTimeline>(
    "/api/v1/platform/demo-sessions/" + encodeURIComponent(sessionId) + "/timeline"
  );
}

export function convertPlatformDemoToPilot(
  sessionId: string,
  payload: { adminName: string; adminEmail: string }
) {
  return apiRequest<PlatformDemoConversion>(
    "/api/v1/platform/demo-sessions/" + encodeURIComponent(sessionId) + "/convert-to-pilot",
    { method: "POST", body: JSON.stringify(payload) }
  );
}

export function provisionPlatformBusiness(payload: PlatformBusinessProvisioningRequest) {
  return apiRequest<PlatformBusinessProvisioningResponse>("/api/v1/platform/businesses", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function getPlatformEconomics() {
  return apiRequest<PlatformEconomics>("/api/v1/platform/economics");
}
