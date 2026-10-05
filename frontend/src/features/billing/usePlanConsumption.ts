import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createBillingCheckout,
  getBillingStatus,
  getCurrentUser,
  getPublicPlans,
  getSubscription,
  getUsageStatus,
  getUsageSummary,
  type BillingStatus,
  type Subscription,
  type UsageItem
} from "./api";

const PRIVILEGED_ROLES = new Set(["BUSINESS_ADMIN", "BUSINESS_OWNER"]);

function usageWindow(subscription?: Subscription) {
  const end = subscription?.currentPeriodEnd ? new Date(subscription.currentPeriodEnd) : new Date();
  const start = subscription?.currentPeriodStart
    ? new Date(subscription.currentPeriodStart)
    : new Date(end.getTime() - 30 * 86_400_000);

  return {
    from: start.toISOString(),
    to: end.toISOString()
  };
}

export function usePlanConsumption() {
  const queryClient = useQueryClient();

  const me = useQuery({
    queryKey: ["auth", "me"],
    queryFn: getCurrentUser
  });

  const subscription = useQuery({
    queryKey: ["billing", "subscription"],
    queryFn: getSubscription
  });

  const roles = me.data?.roles ?? [];
  const canViewDetailedUsage = roles.some(role => PRIVILEGED_ROLES.has(role));
  const canManageBilling = roles.includes("BUSINESS_ADMIN");
  const window = usageWindow(subscription.data);

  const usage = useQuery({
    queryKey: ["usage", "summary", window.from, window.to],
    queryFn: () => getUsageSummary(window.from, window.to),
    enabled: Boolean(subscription.data) && canViewDetailedUsage
  });

  const usageStatus = useQuery({
    queryKey: ["usage", "status"],
    queryFn: getUsageStatus,
    enabled: Boolean(subscription.data) && canViewDetailedUsage
  });

  const billingStatus = useQuery({
    queryKey: ["billing", "status"],
    queryFn: getBillingStatus,
    enabled: canManageBilling
  });

  const publicPlans = useQuery({
    queryKey: ["billing", "public-plans"],
    queryFn: getPublicPlans,
    enabled: canManageBilling
  });

  const checkout = useMutation({
    mutationFn: createBillingCheckout,
    onSuccess: response => {
      queryClient.setQueryData<BillingStatus>(["billing", "status"], current => {
        if (!current) return current;
        return {
          ...current,
          provider: current.provider || "mercadopago",
          pendingPlanCode: response.planCode,
          pendingPlanName: response.planName,
          pendingMonthlyPriceClp: response.monthlyPriceClp,
          checkoutUrl: response.checkoutUrl,
          awaitingProviderVerification: true
        };
      });
    }
  });

  return {
    me,
    subscription,
    usage,
    usageStatus,
    billingStatus,
    publicPlans,
    checkout,
    canViewDetailedUsage,
    canManageBilling
  };
}

export function usageMetrics(items: UsageItem[] | undefined, fallbackMinutes: number) {
  const values = items ?? [];
  const voice = values.find(item => item.meterKey === "VOICE_SECONDS");
  const messages = values.filter(item =>
    item.meterKey === "WHATSAPP_MESSAGES" || item.meterKey === "SMS_MESSAGES"
  );

  const calls = Math.max(0, Number(voice?.eventCount ?? 0));
  const minutesFromUsage = voice
    ? Math.max(0, Math.ceil(Number(voice.quantity ?? 0) / 60))
    : Math.max(0, fallbackMinutes);
  const messageCount = messages.reduce((total, item) => total + Math.max(0, Number(item.quantity ?? 0)), 0);

  return {
    calls,
    minutes: minutesFromUsage,
    messages: Math.round(messageCount)
  };
}
