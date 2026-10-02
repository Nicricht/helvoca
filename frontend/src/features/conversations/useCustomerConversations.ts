import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { ApiError } from "../../api/client";
import {
  getCallDetail,
  getCalls,
  getWhatsAppConversations,
  getWhatsAppDetail,
  unwrapCalls,
  type ConversationKind
} from "./api";

export interface ConversationReference {
  kind: ConversationKind;
  id: string;
}

export interface CustomerConversationItem extends ConversationReference {
  customerId?: string | null;
  contact: string;
  occurredAt?: string | null;
  status?: string | null;
  resolution?: string | null;
}

const queryDefaults = {
  retry: false,
  refetchOnWindowFocus: false
} as const;

function timestamp(value?: string | null) {
  const parsed = Date.parse(value ?? "");
  return Number.isFinite(parsed) ? parsed : 0;
}

export function useCustomerConversations(customerId?: string | null) {
  const enabled = Boolean(customerId);

  const calls = useQuery({
    queryKey: ["conversations", "calls"],
    queryFn: getCalls,
    enabled,
    ...queryDefaults
  });

  const whatsapp = useQuery({
    queryKey: ["conversations", "whatsapp"],
    queryFn: getWhatsAppConversations,
    enabled,
    ...queryDefaults
  });

  const items = useMemo<CustomerConversationItem[]>(() => {
    if (!customerId) return [];

    const callItems: CustomerConversationItem[] = unwrapCalls(calls.data)
      .filter(call => call.customerId === customerId)
      .map(call => ({
        kind: "call",
        id: call.id,
        customerId: call.customerId,
        contact: call.callerNumber || "Número oculto",
        occurredAt: call.startedAt,
        status: call.status,
        resolution: call.resolution
      }));

    const whatsappItems: CustomerConversationItem[] = (whatsapp.data ?? [])
      .filter(conversation => conversation.customerId === customerId)
      .map(conversation => ({
        kind: "whatsapp",
        id: conversation.id,
        customerId: conversation.customerId,
        contact: conversation.sender || "Número desconocido",
        occurredAt: conversation.lastMessageAt ?? conversation.openedAt,
        status: "WHATSAPP",
        resolution: null
      }));

    return [...callItems, ...whatsappItems].sort(
      (a, b) => timestamp(b.occurredAt) - timestamp(a.occurredAt)
    );
  }, [calls.data, customerId, whatsapp.data]);

  const partialMessages: string[] = [];
  if (calls.isError && whatsapp.isSuccess) {
    partialMessages.push("No pudimos cargar las llamadas. WhatsApp sigue disponible.");
  }
  if (whatsapp.isError && calls.isSuccess) {
    partialMessages.push("No pudimos cargar WhatsApp. Las llamadas siguen disponibles.");
  }

  const bothFailed = calls.isError && whatsapp.isError;
  const forbidden =
    bothFailed &&
    [calls.error, whatsapp.error].some(
      error => error instanceof ApiError && error.status === 403
    );

  return {
    calls,
    whatsapp,
    items,
    loading: enabled && calls.isPending && whatsapp.isPending,
    partial: partialMessages,
    error: bothFailed,
    forbidden
  };
}

export function useConversationDetail(
  reference: ConversationReference | null
) {
  return useQuery({
    queryKey: [
      "conversations",
      "detail",
      reference?.kind ?? "none",
      reference?.id ?? "none"
    ],
    queryFn: () => {
      if (!reference) throw new Error("Conversation reference required");
      return reference.kind === "call"
        ? getCallDetail(reference.id)
        : getWhatsAppDetail(reference.id);
    },
    enabled: Boolean(reference),
    ...queryDefaults
  });
}
