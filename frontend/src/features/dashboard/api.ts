import { apiRequest } from "../../api/client";

export type CurrentUser = {
  email: string;
  roles: string[];
  permissions?: string[];
};

export type OnboardingStatus = {
  businessProfileConfigured: boolean;
  servicesConfigured: boolean;
  scheduleConfigured: boolean;
  knowledgeConfigured: boolean;
  humanTransferConfigured: boolean;
  phoneConfigured: boolean;
  readyForCalls: boolean;
  nextStep: string | null;
};

export type DashboardCall = {
  id: string;
  callerNumber: string | null;
  status: string | null;
  resolution: string | null;
  startedAt: string;
  durationSeconds: number | null;
  estimatedTotalCostUsd: number | null;
};

export type DashboardRequest = {
  id: string;
  type: string | null;
  title: string;
  priority: string | null;
  status: string | null;
  createdAt: string;
};

export type DashboardQuestion = {
  id: string;
  question: string;
  occurrences: number;
  lastSeenAt: string;
};

export type OperationsDashboard = {
  businessName: string;
  timezone: string;
  localNow: string;
  callsToday: number;
  callDurationSecondsToday: number;
  bookingsToday: number;
  newCustomersToday: number;
  openRequests: number;
  unansweredQuestions: number;
  callFailuresToday: number;
  estimatedCallCostTodayUsd: number;
  recentCalls: DashboardCall[];
  recentRequests: DashboardRequest[];
  unanswered: DashboardQuestion[];
};

export type CurrencyTotal = {
  currency: string;
  amount: number;
};

export type DailySalesPoint = {
  date: string;
  paidOrders: number;
  revenue: number;
};

export type SalesAnalytics = {
  days: number;
  timezone: string;
  primaryCurrency: string | null;
  totalRevenue: number | null;
  paidOrders: number;
  unitsSold: number;
  averageTicket: number | null;
  revenueChangePercent?: number | null;
  currencyTotals: CurrencyTotal[];
  salesOverTime: DailySalesPoint[];
  recepVozOrders: number;
  recepVozRevenue: number | null;
  bookingCurrency: string | null;
  paidBookings: number;
  bookingRevenue: number | null;
  recepVozPaidBookings: number;
  recepVozBookingRevenue: number | null;
  bookingCurrencyTotals: CurrencyTotal[];
};

export function getCurrentUser(): Promise<CurrentUser> {
  return apiRequest<CurrentUser>("/api/v1/auth/me");
}

export function getOnboardingStatus(): Promise<OnboardingStatus> {
  return apiRequest<OnboardingStatus>("/api/v1/onboarding/status");
}

export function getOperationsDashboard(): Promise<OperationsDashboard> {
  return apiRequest<OperationsDashboard>("/api/v1/operations/dashboard");
}

export function getSalesAnalytics(days = 7): Promise<SalesAnalytics> {
  const boundedDays = Math.max(1, Math.min(days, 365));
  return apiRequest<SalesAnalytics>("/api/v1/commercial/analytics?days=" + boundedDays);
}
