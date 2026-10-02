import { apiRequest } from "../../api/client";

export type ConversationKind = "call" | "whatsapp";

export interface CallListItem {
  id: string;
  customerId?: string | null;
  callerNumber?: string | null;
  destinationNumber?: string | null;
  direction?: string | null;
  status?: string | null;
  startedAt?: string | null;
  endedAt?: string | null;
  durationSeconds?: number | null;
  resolution?: string | null;
}

export interface CallPage {
  content?: CallListItem[];
}

export interface CallTranscriptItem {
  id: string;
  speaker?: string | null;
  content?: string | null;
  sequenceNumber?: number | null;
  createdAt?: string | null;
}

export interface CallActionItem {
  id: string;
  actionType?: string | null;
  success?: boolean;
  entityType?: string | null;
  entityId?: string | null;
  detail?: string | null;
  errorCode?: string | null;
  createdAt?: string | null;
}

export interface CallDetail {
  call: CallListItem;
  transcript?: CallTranscriptItem[];
  summary?: string | null;
  actions?: CallActionItem[];
}

export interface WhatsAppConversationItem {
  id: string;
  customerId?: string | null;
  channel?: string | null;
  sender?: string | null;
  recipient?: string | null;
  openedAt?: string | null;
  lastMessageAt?: string | null;
}

export interface WhatsAppMessageItem {
  id: string;
  direction?: string | null;
  role?: string | null;
  content?: string | null;
  createdAt?: string | null;
}

export interface WhatsAppDetail {
  conversation: WhatsAppConversationItem;
  messages?: WhatsAppMessageItem[];
}

export function getCalls() {
  return apiRequest<CallPage | CallListItem[]>(
    "/api/v1/calls?size=100&sort=startedAt,desc"
  );
}

export function getCallDetail(id: string) {
  return apiRequest<CallDetail>(
    `/api/v1/calls/${encodeURIComponent(id)}`
  );
}

export function getWhatsAppConversations() {
  return apiRequest<WhatsAppConversationItem[]>(
    "/api/v1/messaging/conversations"
  );
}

export function getWhatsAppDetail(id: string) {
  return apiRequest<WhatsAppDetail>(
    `/api/v1/messaging/conversations/${encodeURIComponent(id)}`
  );
}

export function unwrapCalls(payload: CallPage | CallListItem[] | undefined) {
  if (Array.isArray(payload)) return payload;
  return Array.isArray(payload?.content) ? payload.content : [];
}
