import type {
  CallActionItem,
  CallDetail,
  CallTranscriptItem,
  WhatsAppMessageItem
} from "./api";

const ACTION_LABELS: Record<string, string> = {
  REQUEST_CREATED: "Solicitud creada",
  CREATE_REQUEST: "Solicitud creada",
  BOOKING_CREATED: "Reserva creada",
  CREATE_BOOKING: "Reserva creada",
  BOOKING_RESCHEDULED: "Reserva reprogramada",
  RESCHEDULE_BOOKING: "Reserva reprogramada",
  BOOKING_CANCELLED: "Reserva cancelada",
  CANCEL_BOOKING: "Reserva cancelada",
  CUSTOMER_REGISTERED: "Cliente registrado",
  REGISTER_CALLER: "Cliente registrado",
  CALLER_LOOKUP: "Cliente identificado",
  FIND_CALLER: "Cliente identificado",
  KNOWLEDGE_SEARCH: "Consultó información",
  SEARCH_KNOWLEDGE: "Consultó información",
  SERVICES_LISTED: "Consultó servicios",
  LIST_SERVICES: "Consultó servicios",
  AVAILABILITY_LISTED: "Consultó horarios disponibles",
  LIST_AVAILABLE_SLOTS: "Consultó horarios disponibles",
  AVAILABILITY_CHECKED: "Consultó disponibilidad",
  CHECK_BOOKING_AVAILABILITY: "Consultó disponibilidad",
  HUMAN_TRANSFER: "Derivación a una persona",
  TRANSFER_TO_HUMAN: "Derivación a una persona",
  UNANSWERED_QUESTION_RECORDED: "Pregunta pendiente para revisar"
};

const RESOLUTION_LABELS: Record<string, string> = {
  BOOKING_CREATED: "Reserva creada",
  REQUEST_CREATED: "Solicitud creada",
  HUMAN_TRANSFER: "Derivado a una persona",
  TRANSFER_TO_HUMAN: "Derivado a una persona",
  UNANSWERED_QUESTION_RECORDED: "Pregunta pendiente para revisar",
  COMPLETED: "Atención finalizada",
  NO_ACTION: "Sin acción"
};

const STATUS_LABELS: Record<string, string> = {
  COMPLETED: "Finalizada",
  FAILED: "Falló",
  IN_PROGRESS: "En curso",
  RINGING: "Entrante",
  ANSWERED: "Atendida",
  CANCELED: "Cancelada",
  CANCELLED: "Cancelada"
};

const ATTENTION_ACTIONS = new Set([
  "HUMAN_TRANSFER",
  "TRANSFER_TO_HUMAN",
  "UNANSWERED_QUESTION_RECORDED"
]);

function readableCode(value?: string | null) {
  if (!value) return "";
  if (!/^[A-Z0-9_]+$/.test(value)) return value;
  return value
    .toLowerCase()
    .split("_")
    .filter(Boolean)
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export function actionLabel(value?: string | null) {
  if (!value) return "Acción registrada";
  return ACTION_LABELS[value] ?? readableCode(value);
}

export function resolutionLabel(value?: string | null) {
  if (!value) return "Sin resultado informado";
  return RESOLUTION_LABELS[value] ?? readableCode(value);
}

export function statusLabel(value?: string | null) {
  if (!value) return "Sin estado";
  return STATUS_LABELS[value] ?? readableCode(value);
}

export function needsHumanAttention(detail?: CallDetail | null) {
  if (!detail) return false;
  const status = String(detail.call?.status ?? "").toUpperCase();
  const resolution = String(detail.call?.resolution ?? "").toUpperCase();
  if (status === "FAILED") return true;
  if (
    resolution.includes("HUMAN") ||
    resolution.includes("TRANSFER") ||
    resolution.includes("UNANSWERED") ||
    resolution.includes("PENDING")
  ) {
    return true;
  }
  return (detail.actions ?? []).some(action =>
    action.success === false ||
    ATTENTION_ACTIONS.has(String(action.actionType ?? "").toUpperCase())
  );
}

export function sortCallTranscript(items: CallTranscriptItem[] = []) {
  return [...items].sort((a, b) => {
    const aSequence = a.sequenceNumber ?? Number.MAX_SAFE_INTEGER;
    const bSequence = b.sequenceNumber ?? Number.MAX_SAFE_INTEGER;
    if (aSequence !== bSequence) return aSequence - bSequence;
    return Date.parse(a.createdAt ?? "") - Date.parse(b.createdAt ?? "");
  });
}

export function sortWhatsAppMessages(items: WhatsAppMessageItem[] = []) {
  return [...items].sort(
    (a, b) => Date.parse(a.createdAt ?? "") - Date.parse(b.createdAt ?? "")
  );
}

function turnRole(item: CallTranscriptItem | WhatsAppMessageItem) {
  const candidate = item as Partial<CallTranscriptItem & WhatsAppMessageItem>;
  return String(
    candidate.speaker ?? candidate.role ?? candidate.direction ?? ""
  ).toUpperCase();
}

export function isCustomerTurn(
  item: CallTranscriptItem | WhatsAppMessageItem
) {
  return ["USER", "CALLER", "INBOUND"].includes(turnRole(item));
}

export function isRecepVozTurn(
  item: CallTranscriptItem | WhatsAppMessageItem
) {
  return ["ASSISTANT", "AI", "OUTBOUND"].includes(turnRole(item));
}

export function summarizeActions(actions: CallActionItem[] = []) {
  return actions.map(action => actionLabel(action.actionType));
}
