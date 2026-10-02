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
