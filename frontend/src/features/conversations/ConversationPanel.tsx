import { useEffect, useMemo, useState } from "react";
import type { CallDetail, WhatsAppDetail } from "./api";
import {
  actionLabel,
  isCustomerTurn,
  isRecepVozTurn,
  needsHumanAttention,
  resolutionLabel,
  sortCallTranscript,
  sortWhatsAppMessages,
  statusLabel
} from "./presentation";
import {
  useConversationDetail,
  useCustomerConversations,
  type ConversationReference,
  type CustomerConversationItem
} from "./useCustomerConversations";
import styles from "./ConversationPanel.module.css";

export interface EmbeddedConversationContext {
  channel?: string | null;
  sourceReferenceId?: string | null;
  call?: CallDetail | null;
  whatsapp?: WhatsAppDetail | null;
}

export interface ConversationPanelProps {
  customerId?: string | null;
  customerLabel?: string | null;
  preferredConversation?: ConversationReference | null;
  context?: EmbeddedConversationContext | null;
}

type UnifiedTurn = {
  id: string;
  customer: boolean;
  recepvoz: boolean;
  content: string;
  createdAt?: string | null;
};

function formatDate(value?: string | null) {
  if (!value) return "Sin fecha";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Sin fecha";
  return new Intl.DateTimeFormat("es-CL", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

function formatTime(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("es-CL", {
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function keyOf(item: ConversationReference) {
  return `${item.kind}:${item.id}`;
}

function sessionLabel(item: CustomerConversationItem) {
  if (item.kind === "whatsapp") return "WhatsApp";
  return statusLabel(item.status);
}

function callTurns(detail: CallDetail): UnifiedTurn[] {
  return sortCallTranscript(detail.transcript ?? []).map(item => ({
    id: item.id,
    customer: isCustomerTurn(item),
    recepvoz: isRecepVozTurn(item),
    content: item.content ?? "",
    createdAt: item.createdAt
  }));
}

function whatsappTurns(detail: WhatsAppDetail): UnifiedTurn[] {
  return sortWhatsAppMessages(detail.messages ?? []).map(item => ({
    id: item.id,
    customer: isCustomerTurn(item),
    recepvoz: isRecepVozTurn(item),
    content: item.content ?? "",
    createdAt: item.createdAt
  }));
}

function latestCustomerQuestion(turns: UnifiedTurn[]) {
  const customerTurns = turns.filter(
    turn => turn.customer && turn.content.trim()
  );
  return (
    [...customerTurns].reverse().find(turn => turn.content.includes("?"))?.content ??
    customerTurns.at(-1)?.content ??
    "No hay una pregunta del cliente guardada."
  );
}

function recepVozActivity(
  kind: ConversationReference["kind"],
  detail: CallDetail | WhatsAppDetail,
  turns: UnifiedTurn[]
) {
  if (kind === "call") {
    const labels = (detail as CallDetail).actions?.map(action =>
      actionLabel(action.actionType)
    ) ?? [];
    if (labels.length) return labels;
  }

  if (turns.some(turn => turn.recepvoz)) {
    return [
      kind === "call"
        ? "Respondió durante la llamada"
        : "Respondió al cliente por WhatsApp"
    ];
  }

  return ["No hay una acción de RecepVoz registrada."];
}

function detailSummary(
  kind: ConversationReference["kind"],
  detail: CallDetail | WhatsAppDetail
) {
  if (kind === "call") {
    const call = detail as CallDetail;
    return {
      status: resolutionLabel(call.call.resolution),
      summary:
        call.summary?.trim() ||
        "La llamada está registrada, pero no tiene un resumen guardado."
    };
  }
  return {
    status: "Conversación registrada",
    summary: "Revisa los mensajes para confirmar qué pidió el cliente y qué respondió RecepVoz."
  };
}

export function ConversationPanel({
  customerId,
  customerLabel,
  preferredConversation,
  context
}: ConversationPanelProps) {
  const contextualReference: ConversationReference | null = context?.call
    ? { kind: "call", id: context.call.call.id }
    : context?.whatsapp
      ? { kind: "whatsapp", id: context.whatsapp.conversation.id }
      : null;
  const hasContext = context !== undefined;
  const workspace = useCustomerConversations(hasContext ? null : customerId);
  const [selectedKey, setSelectedKey] = useState<string | null>(
    preferredConversation ? keyOf(preferredConversation) : null
  );

  useEffect(() => {
    if (hasContext) return;

    if (!workspace.items.length) {
      setSelectedKey(null);
      return;
    }

    const preferredKey = preferredConversation
      ? keyOf(preferredConversation)
      : null;
    const currentStillExists = workspace.items.some(
      item => keyOf(item) === selectedKey
    );

    if (preferredKey && workspace.items.some(item => keyOf(item) === preferredKey)) {
      setSelectedKey(preferredKey);
    } else if (!currentStillExists) {
      setSelectedKey(keyOf(workspace.items[0]));
    }
  }, [hasContext, preferredConversation, selectedKey, workspace.items]);

  const selected = contextualReference ?? (
    workspace.items.find(item => keyOf(item) === selectedKey) ?? null
  );

  const detail = useConversationDetail(
    !hasContext && selected ? { kind: selected.kind, id: selected.id } : null
  );

  const resolvedDetail = context?.call ?? context?.whatsapp ?? detail.data;

  const view = useMemo(() => {
    if (!selected || !resolvedDetail) return null;

    const raw = resolvedDetail as CallDetail | WhatsAppDetail;
    const turns =
      selected.kind === "call"
        ? callTurns(raw as CallDetail)
        : whatsappTurns(raw as WhatsAppDetail);
    const summary = detailSummary(selected.kind, raw);
    const activity = recepVozActivity(selected.kind, raw, turns);
    const needsAttention =
      selected.kind === "call"
        ? needsHumanAttention(raw as CallDetail)
        : false;

    return {
      turns,
      summary,
      activity,
      needsAttention,
      question: latestCustomerQuestion(turns)
    };
  }, [resolvedDetail, selected]);

  if (!hasContext && !customerId) {
    return (
      <section className={styles.state} aria-label="Conversación relacionada">
        <strong>Selecciona una reserva</strong>
        <p>La conversación aparecerá aquí cuando exista contexto de cliente.</p>
      </section>
    );
  }

  if (!hasContext && workspace.forbidden) {
    return (
      <section className={styles.state} role="alert" aria-label="Conversación relacionada">
        <strong>Sin acceso a conversaciones</strong>
        <p>Tu cuenta no tiene permiso para revisar este historial.</p>
      </section>
    );
  }

  if (!hasContext && workspace.error) {
    return (
      <section className={styles.state} role="alert" aria-label="Conversación relacionada">
        <strong>No pudimos cargar el historial</strong>
        <p>Las fuentes de llamadas y WhatsApp no están disponibles en este momento.</p>
      </section>
    );
  }

  if (!hasContext && workspace.loading) {
    return (
      <section className={styles.state} role="status" aria-label="Conversación relacionada">
        <strong>Cargando conversación…</strong>
      </section>
    );
  }

  if ((hasContext && !contextualReference) || (!hasContext && !workspace.items.length)) {
    return (
      <section className={styles.state} aria-label="Conversación relacionada">
        <strong>Sin conversación relacionada</strong>
        <p>
          {customerLabel || "Este cliente"} no tiene llamadas o WhatsApp disponibles
          para mostrar en esta reserva.
        </p>
      </section>
    );
  }

  return (
    <section className={styles.panel} aria-label="Conversación relacionada">
      <header className={styles.header}>
        <div>
          <span className={styles.eyebrow}>HISTORIAL RELACIONADO</span>
          <h3>Conversación</h3>
        </div>
        <span className={styles.readOnly}>Solo lectura</span>
      </header>

      {!hasContext && workspace.partial.length > 0 && (
        <div className={styles.partial} role="status">
          {workspace.partial.join(" ")}
        </div>
      )}

      {!hasContext && (
        <div className={styles.sessions} aria-label="Conversaciones del cliente">
          {workspace.items.map(item => {
            const active = selected ? keyOf(item) === keyOf(selected) : false;
            return (
              <button
                key={keyOf(item)}
                type="button"
                className={active ? styles.sessionActive : styles.session}
                aria-pressed={active}
                onClick={() => setSelectedKey(keyOf(item))}
              >
                <span className={styles.channel}>
                  {item.kind === "call" ? "Llamada" : "WhatsApp"}
                </span>
                <strong>{sessionLabel(item)}</strong>
                <small>{formatDate(item.occurredAt)}</small>
              </button>
            );
          })}
        </div>
      )}

      {!hasContext && detail.isPending && (
        <div className={styles.detailState} role="status">
          Cargando detalle…
        </div>
      )}

      {!hasContext && detail.isError && (
        <div className={styles.detailState} role="alert">
          No pudimos abrir esta conversación.
        </div>
      )}

      {selected && view && (
        <div className={styles.detail}>
          <div className={styles.operationalGrid}>
            <article>
              <span>Qué preguntó el cliente</span>
              <strong>{view.question}</strong>
            </article>
            <article>
              <span>Qué hizo RecepVoz</span>
              <strong>{view.activity[0]}</strong>
              {view.activity.slice(1).map(label => (
                <small key={label}>✓ {label}</small>
              ))}
            </article>
            <article className={view.needsAttention ? styles.attention : undefined}>
              <span>Necesitas actuar</span>
              <strong>
                {view.needsAttention
                  ? "Sí, esta atención requiere revisión."
                  : "No hay intervención humana marcada."}
              </strong>
            </article>
          </div>

          <article className={styles.summary}>
            <div>
              <span>Resultado</span>
              <strong>{view.summary.status}</strong>
            </div>
            <p>{view.summary.summary}</p>
          </article>

          <div
            className={styles.transcript}
            data-testid="conversation-transcript"
            aria-label="Transcripción de la conversación"
          >
            {view.turns.length === 0 ? (
              <p className={styles.noTurns}>No hay mensajes o transcripción disponibles.</p>
            ) : (
              view.turns.map(turn => (
                <article
                  key={turn.id}
                  className={
                    turn.customer
                      ? styles.customerTurn
                      : turn.recepvoz
                        ? styles.aiTurn
                        : styles.otherTurn
                  }
                >
                  <div className={styles.turnMeta}>
                    <strong>
                      {turn.customer
                        ? "Cliente"
                        : turn.recepvoz
                          ? "RecepVoz"
                          : "Participante"}
                    </strong>
                    <time>{formatTime(turn.createdAt)}</time>
                  </div>
                  <p>{turn.content}</p>
                </article>
              ))
            )}
          </div>
        </div>
      )}
    </section>
  );
}
