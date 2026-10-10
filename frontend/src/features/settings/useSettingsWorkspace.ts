import { useQuery } from "@tanstack/react-query";
import {
  getAiAgent,
  getBusiness,
  getBusinessHours,
  getBusinessProfile,
  getCurrentUser,
  getKnowledge,
  getOnboardingStatus,
  getPhoneNumbers,
  getServices,
  getVoices
} from "./api";

const queryDefaults = {
  retry: false,
  refetchOnWindowFocus: false
} as const;

const LEGACY_MANAGE_ROLES = new Set(["BUSINESS_ADMIN", "BUSINESS_OWNER"]);

export function useSettingsWorkspace() {
  const me = useQuery({
    queryKey: ["auth", "me"],
    queryFn: getCurrentUser,
    ...queryDefaults
  });

  const business = useQuery({
    queryKey: ["settings", "business"],
    queryFn: getBusiness,
    ...queryDefaults
  });

  const profile = useQuery({
    queryKey: ["settings", "business-profile"],
    queryFn: getBusinessProfile,
    ...queryDefaults
  });

  const onboarding = useQuery({
    queryKey: ["settings", "onboarding-status"],
    queryFn: getOnboardingStatus,
    ...queryDefaults
  });

  const services = useQuery({
    queryKey: ["settings", "services"],
    queryFn: getServices,
    ...queryDefaults
  });

  const hours = useQuery({
    queryKey: ["settings", "business-hours"],
    queryFn: getBusinessHours,
    ...queryDefaults
  });

  const knowledge = useQuery({
    queryKey: ["settings", "knowledge"],
    queryFn: getKnowledge,
    ...queryDefaults
  });

  const agent = useQuery({
    queryKey: ["settings", "ai-agent"],
    queryFn: getAiAgent,
    ...queryDefaults
  });

  const voices = useQuery({
    queryKey: ["settings", "voices"],
    queryFn: getVoices,
    ...queryDefaults
  });

  const phones = useQuery({
    queryKey: ["settings", "phone-numbers"],
    queryFn: getPhoneNumbers,
    ...queryDefaults
  });

  const roles = me.data?.roles ?? [];
  const permissions = me.data?.permissions;
  const hasPermissionClaims = Array.isArray(permissions);
  // The save endpoints require BUSINESS_ADMIN, not only permission claims.
  const canManage = roles.includes("BUSINESS_ADMIN");

  const canManageTeam = hasPermissionClaims
    ? permissions.includes("TEAM_MANAGE") || roles.some(role => LEGACY_MANAGE_ROLES.has(role))
    : roles.some(role => LEGACY_MANAGE_ROLES.has(role));

  const canReadScheduleExceptions = roles.includes("BUSINESS_ADMIN") || roles.includes("OPERATOR");
  const canManageScheduleExceptions = roles.includes("BUSINESS_ADMIN");
  const canReadChannels = roles.includes("BUSINESS_ADMIN") || roles.includes("OPERATOR");
  const canManageChannels = roles.includes("BUSINESS_ADMIN");
  const canReadIntegrations = roles.includes("BUSINESS_ADMIN") || roles.includes("OPERATOR");
  const canManageIntegrations = roles.includes("BUSINESS_ADMIN");

  const primaryLoading = me.isPending || business.isPending;
  const primaryError = me.isError || business.isError;

  const partialErrors = [
    profile.isError ? "perfil público" : null,
    onboarding.isError ? "estado de preparación" : null,
    services.isError ? "servicios" : null,
    hours.isError ? "horarios" : null,
    knowledge.isError ? "conocimiento" : null,
    agent.isError ? "recepcionista IA" : null,
    voices.isError ? "voces" : null,
    phones.isError ? "canales" : null
  ].filter((value): value is string => Boolean(value));

  return {
    me,
    business,
    profile,
    onboarding,
    services,
    hours,
    knowledge,
    agent,
    voices,
    phones,
    canManage,
    canManageTeam,
    canReadScheduleExceptions,
    canManageScheduleExceptions,
    canReadChannels,
    canManageChannels,
    canReadIntegrations,
    canManageIntegrations,
    primaryLoading,
    primaryError,
    partialErrors
  };
}
