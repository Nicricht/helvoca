import { apiRequest } from "../../api/client";

export interface CurrentUser {
  email?: string;
  roles?: string[];
  permissions?: string[];
}

export type OrderStatus =
  | "CONFIRMED"
  | "PREPARING"
  | "READY"
  | "DISPATCHED"
  | "COMPLETED"
  | "CANCELLED";

export type OrderSource = "VOICE" | "WHATSAPP" | "MANUAL" | "API";
export type FulfillmentType = "PICKUP" | "DELIVERY";

export interface OrderLine {
  catalogItemId?: string | null;
  name: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  notes?: string | null;
}

export interface BusinessOrder {
  id: string;
  operationId?: string | null;
  sourceReferenceId?: string | null;
  status: OrderStatus;
  fulfillmentType: FulfillmentType;
  contactName?: string | null;
  contactPhone?: string | null;
  deliveryAddress?: string | null;
  subtotal: number;
  deliveryFee: number;
  total: number;
  currency: string;
  source: OrderSource;
  lines?: OrderLine[];
  createdAt: string;
  updatedAt?: string | null;
}

export interface Delivery {
  id: string;
  operationId?: string | null;
  orderId: string;
  status?: string | null;
  deliveryAddress?: string | null;
  fee?: number | null;
  notes?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface OperationEvent {
  id: string;
  eventType: string;
  actorType?: string | null;
  channel?: string | null;
  createdAt?: string | null;
}

export interface ConversationMessage {
  id: string;
  direction?: string | null;
  role?: string | null;
  content?: string | null;
  createdAt?: string | null;
}

export interface ConversationContext {
  conversation?: {
    id?: string;
    sender?: string | null;
    openedAt?: string | null;
    lastMessageAt?: string | null;
  } | null;
  messages?: ConversationMessage[];
}

export interface CallContext {
  id?: string;
  transcript?: string | null;
  summary?: string | null;
  status?: string | null;
}

export function getCurrentUser() {
  return apiRequest<CurrentUser>("/api/v1/auth/me");
}

export function getOrders() {
  return apiRequest<BusinessOrder[]>("/api/v1/commercial/orders");
}

export function getDeliveries() {
  return apiRequest<Delivery[]>("/api/v1/commercial/deliveries");
}

export function getOperationEvents(operationId: string) {
  return apiRequest<OperationEvent[]>(
    `/api/v1/operation-events?operationId=${encodeURIComponent(operationId)}`
  );
}

export function getConversation(sourceReferenceId: string) {
  return apiRequest<ConversationContext>(
    `/api/v1/messaging/conversations/${encodeURIComponent(sourceReferenceId)}`
  );
}

export function getCallContext(sourceReferenceId: string) {
  return apiRequest<CallContext>(
    `/api/v1/calls/${encodeURIComponent(sourceReferenceId)}`
  );
}

export function updateOrderStatus(orderId: string, status: OrderStatus) {
  return apiRequest<BusinessOrder>(
    `/api/v1/commercial/orders/${encodeURIComponent(orderId)}/status`,
    {
      method: "PATCH",
      body: JSON.stringify({ status })
    }
  );
}

export function updatePreparationStatus(orderId: string, status: OrderStatus) {
  return apiRequest<BusinessOrder>(
    `/api/v1/commercial/orders/${encodeURIComponent(orderId)}/preparation-status`,
    {
      method: "PATCH",
      body: JSON.stringify({ status })
    }
  );
}
