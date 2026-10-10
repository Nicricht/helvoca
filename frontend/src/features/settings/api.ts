import { apiRequest } from "../../api/client";

export interface CurrentUser {
  email?: string;
  roles?: string[];
  permissions?: string[];
}

export interface BusinessSummary {
  id?: string;
  name?: string;
  timezone?: string;
  language?: string;
  humanTransferPhone?: string | null;
}

export interface BusinessProfile {
  businessId?: string;
  presetKey?: string | null;
  publicDescription?: string | null;
  publicPhone?: string | null;
  publicEmail?: string | null;
  websiteUrl?: string | null;
  addressLine?: string | null;
  commune?: string | null;
  city?: string | null;
  region?: string | null;
  countryCode?: string | null;
  defaultCurrency?: string | null;
  sellsProducts?: boolean | null;
  sellsServices?: boolean | null;
  usesReservations?: boolean | null;
}

export interface OnboardingStatus {
  businessProfileConfigured?: boolean;
  servicesConfigured?: boolean;
  scheduleConfigured?: boolean;
  knowledgeConfigured?: boolean;
  humanTransferConfigured?: boolean;
  phoneConfigured?: boolean;
  readyForCalls?: boolean;
  nextStep?: string;
}

export interface ServiceItem {
  id?: string | null;
  name: string;
  description?: string | null;
  durationMinutes: number;
  price?: number | null;
  active?: boolean;
}

export interface BusinessHour {
  dayOfWeek: number;
  openTime: string;
  closeTime: string;
}

export interface KnowledgeItem {
  id?: string | null;
  title: string;
  category?: string | null;
  content: string;
  active?: boolean;
}

export interface AiAgent {
  configured?: boolean;
  name?: string;
  language?: string;
  voice?: string;
  greeting?: string;
  instructions?: string;
  active?: boolean;
  capabilities?: string[];
}

export interface VoiceOption {
  code?: string;
  selection?: string;
  name?: string;
  description?: string;
}

export interface PhoneNumber {
  id?: string;
  provider?: string;
  externalId?: string | null;
  phoneNumber?: string;
  active?: boolean;
  whatsappEnabled?: boolean;
}



export interface TeamMember {
  id?: string;
  name?: string;
  email?: string;
  active?: boolean;
  roles?: string[];
}

export interface TeamInvitation {
  id: string;
  businessId?: string;
  businessName?: string;
  name: string;
  email: string;
  role: string;
  expiresAt?: string;
  status?: string;
  invitePath?: string | null;
}

export interface InviteUserInput {
  name: string;
  email: string;
  role: string;
}

export interface ScheduleException {
  id?: string;
  exceptionDate: string;
  closed: boolean;
  openTime?: string | null;
  closeTime?: string | null;
  reason?: string | null;
}

export interface ScheduleExceptionInput {
  closed: boolean;
  openTime: string | null;
  closeTime: string | null;
  reason: string | null;
}



export interface PhoneProvisioningStatus {
  enabled: boolean;
  configured: boolean;
  purchaseAvailable: boolean;
  provider?: string | null;
  message?: string | null;
}

export interface AvailablePhoneNumber {
  phoneNumber: string;
  friendlyName?: string | null;
  locality?: string | null;
  region?: string | null;
  postalCode?: string | null;
  isoCountry?: string | null;
  addressRequirements?: string | null;
  voiceCapable?: boolean;
}

export interface MetaWhatsAppStatus {
  status?: string;
  configured?: boolean;
  enabled?: boolean;
  provider?: string | null;
  phoneRecordId?: string | null;
  phoneNumber?: string | null;
  phone_number_id?: string | null;
  waba_id?: string | null;
  credentialReferenceConfigured?: boolean;
  certifiedAt?: string | null;
}

export interface MetaWhatsAppBootstrap {
  enabled: boolean;
  available: boolean;
  appId?: string | null;
  configId?: string | null;
  graphApiVersion?: string | null;
}

export interface MetaWabaCandidate {
  id: string;
  name?: string | null;
  currency?: string | null;
  timezoneId?: string | null;
  messageTemplateNamespace?: string | null;
  systemUserAssigned?: boolean;
}

export interface MetaAuthorizationResult {
  state?: string;
  accepted?: boolean;
  retained?: boolean;
  exchangePending?: boolean;
  wabas?: MetaWabaCandidate[];
  wabaAfterCursor?: string | null;
}

export interface MetaPhoneCandidate {
  id: string;
  displayPhoneNumber?: string | null;
  verifiedName?: string | null;
  qualityRating?: string | null;
  codeVerificationStatus?: string | null;
}

export interface MetaPhoneDiscoveryResult {
  state?: string;
  appSubscribed?: boolean;
  phoneNumbers?: MetaPhoneCandidate[];
  afterCursor?: string | null;
}

export interface MetaPhoneValidationResult {
  state?: string;
  phoneNumberId?: string | null;
  displayPhoneNumber?: string | null;
  verifiedName?: string | null;
  qualityRating?: string | null;
  codeVerificationStatus?: string | null;
}

export interface MetaPhoneFinalizeResult {
  state?: string;
  phoneRecordId?: string | null;
  provider?: string | null;
  phoneNumberId?: string | null;
  wabaId?: string | null;
  credentialRef?: string | null;
  enabled?: boolean;
}

export interface MetaReadinessBlocker {
  code?: string;
  message?: string;
}

export interface MetaCertificationReadiness {
  state?: string;
  ready?: boolean;
  alreadyCertified?: boolean;
  blockers?: MetaReadinessBlocker[];
}

export interface MetaDeploymentReadiness {
  state?: string;
  readyForTenantStaging?: boolean;
  blockers?: MetaReadinessBlocker[];
}



export interface ManagedPaymentSandboxStatus {
  available: boolean;
  configured: boolean;
  enabled: boolean;
  blockedByCustomConfiguration: boolean;
  provider?: string | null;
  mode?: string | null;
  webhookPath?: string | null;
}

export interface SetupInput {
  businessName: string;
  timezone: string;
  language: string;
  humanTransferPhone: string | null;
  services: Array<{
    id: string | null;
    name: string;
    description: string | null;
    durationMinutes: number;
    price: number | null;
  }>;
  hours: BusinessHour[];
  knowledge: Array<{
    id: string | null;
    title: string;
    category: string | null;
    content: string;
  }>;
}

export interface BusinessProfileInput {
  presetKey: string | null;
  publicDescription: string | null;
  publicPhone: string | null;
  publicEmail: string | null;
  websiteUrl: string | null;
  addressLine: string | null;
  commune: string | null;
  city: string | null;
  region: string | null;
  countryCode: string | null;
  defaultCurrency: string;
  sellsProducts: boolean | null;
  sellsServices: boolean | null;
  usesReservations: boolean | null;
}

export interface AiAgentInput {
  name: string;
  language: string;
  voice: string;
  greeting: string;
  instructions: string;
  active: boolean;
  capabilities: string[];
}

export function getCurrentUser() {
  return apiRequest<CurrentUser>("/api/v1/auth/me");
}

export function getBusiness() {
  return apiRequest<BusinessSummary>("/api/v1/business");
}

export function getBusinessProfile() {
  return apiRequest<BusinessProfile>("/api/v1/business/profile");
}

export function getOnboardingStatus() {
  return apiRequest<OnboardingStatus>("/api/v1/onboarding/status");
}

export function getServices() {
  return apiRequest<ServiceItem[]>("/api/v1/services");
}

export function getBusinessHours() {
  return apiRequest<BusinessHour[]>("/api/v1/business/hours");
}

export function getKnowledge() {
  return apiRequest<KnowledgeItem[]>("/api/v1/knowledge?activeOnly=false");
}

export function getAiAgent() {
  return apiRequest<AiAgent>("/api/v1/ai-agent");
}

export function getVoices() {
  return apiRequest<VoiceOption[]>("/api/v1/ai-agent/voices");
}

export function getPhoneNumbers() {
  return apiRequest<PhoneNumber[]>("/api/v1/phone-numbers");
}

export function saveSetup(input: SetupInput) {
  return apiRequest<OnboardingStatus>("/api/v1/onboarding/setup", {
    method: "PUT",
    body: JSON.stringify(input)
  });
}

export function updateBusiness(input: { name: string; timezone: string; language: string; humanTransferPhone: string | null }) {
  return apiRequest<BusinessSummary>("/api/v1/business", { method: "PATCH", body: JSON.stringify(input) });
}

export function replaceBusinessHours(hours: BusinessHour[]) {
  return apiRequest<BusinessHour[]>("/api/v1/business/hours", { method: "PUT", body: JSON.stringify({ hours }) });
}

export function createServiceItem(input: Omit<ServiceItem, "id">) {
  return apiRequest<ServiceItem>("/api/v1/services", { method: "POST", body: JSON.stringify(input) });
}

export function updateServiceItem(id: string, input: Omit<ServiceItem, "id">) {
  return apiRequest<ServiceItem>(`/api/v1/services/${encodeURIComponent(id)}`, {
    method: "PATCH", body: JSON.stringify(input)
  });
}

export function deactivateServiceItem(id: string) {
  return apiRequest<void>(`/api/v1/services/${encodeURIComponent(id)}`, { method: "DELETE" });
}

export function createKnowledgeItem(input: Omit<KnowledgeItem, "id">) {
  return apiRequest<KnowledgeItem>("/api/v1/knowledge", { method: "POST", body: JSON.stringify(input) });
}

export function updateKnowledgeItem(id: string, input: Omit<KnowledgeItem, "id">) {
  return apiRequest<KnowledgeItem>(`/api/v1/knowledge/${encodeURIComponent(id)}`, {
    method: "PATCH", body: JSON.stringify(input)
  });
}

export function deactivateKnowledgeItem(id: string) {
  return apiRequest<void>(`/api/v1/knowledge/${encodeURIComponent(id)}`, { method: "DELETE" });
}

export function saveBusinessProfile(input: BusinessProfileInput) {
  return apiRequest<BusinessProfile>("/api/v1/business/profile", {
    method: "PUT",
    body: JSON.stringify(input)
  });
}

export function saveAiAgent(input: AiAgentInput) {
  return apiRequest<AiAgent>("/api/v1/ai-agent", {
    method: "PUT",
    body: JSON.stringify(input)
  });
}


export function getTeamMembers() {
  return apiRequest<TeamMember[]>("/api/v1/admin/users");
}

export function getTeamInvitations() {
  return apiRequest<TeamInvitation[]>("/api/v1/admin/invitations");
}

export function createTeamInvitation(input: InviteUserInput) {
  return apiRequest<TeamInvitation>("/api/v1/admin/invitations", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export function revokeTeamInvitation(invitationId: string) {
  return apiRequest<void>(`/api/v1/admin/invitations/${encodeURIComponent(invitationId)}`, {
    method: "DELETE"
  });
}

export function getScheduleExceptions() {
  return apiRequest<ScheduleException[]>("/api/v1/business/schedule-exceptions");
}

export function saveScheduleException(date: string, input: ScheduleExceptionInput) {
  return apiRequest<ScheduleException>(
    `/api/v1/business/schedule-exceptions/${encodeURIComponent(date)}`,
    {
      method: "PUT",
      body: JSON.stringify(input)
    }
  );
}

export function deleteScheduleException(date: string) {
  return apiRequest<void>(
    `/api/v1/business/schedule-exceptions/${encodeURIComponent(date)}`,
    { method: "DELETE" }
  );
}


export function getPhoneProvisioningStatus() {
  return apiRequest<PhoneProvisioningStatus>("/api/v1/phone-numbers/provisioning/status");
}

export function searchAvailablePhoneNumbers(country: string, areaCode: string) {
  const query = new URLSearchParams({ country: country.trim().toUpperCase(), limit: "5" });
  if (areaCode.trim()) query.set("areaCode", areaCode.trim());
  return apiRequest<AvailablePhoneNumber[]>(`/api/v1/phone-numbers/provisioning/available?${query.toString()}`);
}

export function provisionPhoneNumber(phoneNumber: string) {
  return apiRequest<PhoneNumber>("/api/v1/phone-numbers/provisioning", {
    method: "POST",
    body: JSON.stringify({ phoneNumber, confirmed: true })
  });
}

export function connectExistingPhoneNumber(phoneNumber: string) {
  return apiRequest<PhoneNumber>("/api/v1/phone-numbers", {
    method: "POST",
    body: JSON.stringify({ phoneNumber, active: true })
  });
}



export function setPhoneActive(id: string, active: boolean) {
  return apiRequest<PhoneNumber>(`/api/v1/phone-numbers/${encodeURIComponent(id)}/active`, {
    method: "PATCH",
    body: JSON.stringify({ active })
  });
}

export function setPhoneWhatsAppEnabled(id: string, enabled: boolean) {
  return apiRequest<PhoneNumber>(`/api/v1/phone-numbers/${encodeURIComponent(id)}/whatsapp`, {
    method: "PATCH",
    body: JSON.stringify({ enabled })
  });
}

export function detachPhoneNumber(id: string) {
  return apiRequest<void>(`/api/v1/phone-numbers/${encodeURIComponent(id)}`, { method: "DELETE" });
}

export function getMetaWhatsAppStatus() {
  return apiRequest<MetaWhatsAppStatus>("/api/v1/channels/whatsapp/meta/config");
}

export function getMetaWhatsAppBootstrap() {
  return apiRequest<MetaWhatsAppBootstrap>("/api/v1/channels/whatsapp/meta/embedded-signup/bootstrap");
}

export function exchangeMetaAuthorizationCode(code: string) {
  return apiRequest<MetaAuthorizationResult>("/api/v1/channels/whatsapp/meta/embedded-signup/authorization-code", {
    method: "POST",
    body: JSON.stringify({ code })
  });
}

export function discoverMetaPhoneNumbers(wabaId: string) {
  return apiRequest<MetaPhoneDiscoveryResult>("/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-numbers", {
    method: "POST",
    body: JSON.stringify({ wabaId })
  });
}

export function validateMetaPhoneNumber(wabaId: string, phoneNumberId: string) {
  return apiRequest<MetaPhoneValidationResult>("/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-number/validate", {
    method: "POST",
    body: JSON.stringify({ wabaId, phoneNumberId })
  });
}

export function finalizeMetaPhoneNumber(wabaId: string, phoneNumberId: string, pin: string) {
  return apiRequest<MetaPhoneFinalizeResult>("/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-number/finalize", {
    method: "POST",
    body: JSON.stringify({ wabaId, phoneNumberId, pin })
  });
}

export function getMetaCertificationReadiness() {
  return apiRequest<MetaCertificationReadiness>("/api/v1/channels/whatsapp/meta/certification/readiness");
}

export function getMetaDeploymentReadiness() {
  return apiRequest<MetaDeploymentReadiness>("/api/v1/channels/whatsapp/meta/deployment/readiness");
}

export function activateMetaWhatsApp() {
  return apiRequest<MetaWhatsAppStatus>("/api/v1/channels/whatsapp/meta/config/activate", { method: "POST" });
}

export function deactivateMetaWhatsApp() {
  return apiRequest<MetaWhatsAppStatus>("/api/v1/channels/whatsapp/meta/config/deactivate", { method: "POST" });
}


export function getManagedPaymentSandbox() {
  return apiRequest<ManagedPaymentSandboxStatus>("/api/v1/payment-provider/managed-sandbox");
}

export function enableManagedPaymentSandbox() {
  return apiRequest<ManagedPaymentSandboxStatus>("/api/v1/payment-provider/managed-sandbox/enable", {
    method: "POST"
  });
}

export function disableManagedPaymentSandbox() {
  return apiRequest<ManagedPaymentSandboxStatus>("/api/v1/payment-provider/managed-sandbox/disable", {
    method: "POST"
  });
}
