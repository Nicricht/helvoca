import { apiRequest } from "../../api/client";

export interface BusinessIdentity {
  name?: string | null;
}

export interface SimulatorSession {
  sessionId: string;
  greeting: string;
  active?: boolean;
  ended?: boolean;
}

export interface SimulatorReply {
  sessionId?: string;
  reply: string;
  resolution?: string | null;
  actionCount?: number;
  ended?: boolean;
}

export interface SimulatorTraceAction {
  id: string;
  actionType?: string | null;
  success?: boolean;
  detail?: string | null;
  errorCode?: string | null;
}

export interface SimulatorCallTrace {
  call?: {
    id?: string;
    resolution?: string | null;
  } | null;
  actions?: SimulatorTraceAction[] | null;
}

export function getSimulatorBusiness() {
  return apiRequest<BusinessIdentity>("/api/v1/business");
}

export function startSimulatorSession() {
  return apiRequest<SimulatorSession>("/api/v1/simulator/sessions", {
    method: "POST"
  });
}

export function sendSimulatorMessage(sessionId: string, message: string) {
  return apiRequest<SimulatorReply>(
    `/api/v1/simulator/sessions/${encodeURIComponent(sessionId)}/messages`,
    {
      method: "POST",
      body: JSON.stringify({ message })
    }
  );
}

export function finishSimulatorSession(sessionId: string) {
  return apiRequest<void>(
    `/api/v1/simulator/sessions/${encodeURIComponent(sessionId)}/finish`,
    { method: "POST" }
  );
}

export function getSimulatorTrace(sessionId: string) {
  return apiRequest<SimulatorCallTrace>(
    `/api/v1/calls/${encodeURIComponent(sessionId)}`
  );
}
