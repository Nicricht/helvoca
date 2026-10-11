import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowUpRight, CheckCircle2, CircleAlert, Clock3, RefreshCw, UserRoundCheck } from "lucide-react";
import {
  acknowledgeHumanHandoff,
  resolveHumanHandoff,
  updateRequestStatus,
  type HumanAttentionItem
} from "../../features/operations/api";
import styles from "./HomeAttentionRows.module.css";

interface Props {
  items?: HumanAttentionItem[];
  loading: boolean;
  failed: boolean;
  refreshing: boolean;
  canManageRequests: boolean;
  onRefresh: () => void;
}

function readableStatus(item: HumanAttentionItem): string {
  if (item.kind === "HANDOFF") {
    return ({
      OPEN: "Sin atender",
      ACKNOWLEDGED: "Recibido",
      ASSIGNED: "Asignado"
    } as Record<string, string>)[item.status] || item.status;
  }
  return ({
    OPEN: "Abierta",
    IN_PROGRESS: "En progreso",
    RESOLVED: "Resuelta",
    CANCELLED: "Cancelada"
  } as Record<string, string>)[item.status] || item.status;
}

type AttentionAction = "REQUEST_PROGRESS" | "REQUEST_RESOLVE" | "HANDOFF_ACK" | "HANDOFF_RESOLVE";
type AttentionNext = { label: string; action: AttentionAction };

function actionFor(item: HumanAttentionItem, canManageRequests: boolean): AttentionNext | null {
  if (item.kind === "REQUEST" && canManageRequests) {
    if (item.status === "OPEN") return { label: "Empezar gestión", action: "REQUEST_PROGRESS" };
    if (item.status === "IN_PROGRESS") return { label: "Marcar resuelta", action: "REQUEST_RESOLVE" };
  }
  if (item.kind === "HANDOFF") {
    if (item.status === "OPEN") return { label: "Tomar caso", action: "HANDOFF_ACK" };
    if (item.status === "ACKNOWLEDGED" || item.status === "ASSIGNED") {
      return { label: "Cerrar escalamiento", action: "HANDOFF_RESOLVE" };
    }
  }
  return null;
}

/**
 * Operates only on the authoritative /operations/attention read model.
 * Mutations require an explicit user click and use existing secured endpoints.
 * This component never creates an AI conversation or sends a provider message.
 */
export function HomeAttentionRows({
  items, loading, failed, refreshing, canManageRequests, onRefresh
}: Props) {
  const client = useQueryClient();
  const [busyId, setBusyId] = useState<string | null>(null);
  // A synchronous lock also rejects repeated clicks before React commits a render.
  const busyRef = useRef<string | null>(null);
  const [error, setError] = useState("");
  const [confirmationId, setConfirmationId] = useState<string | null>(null);

  async function perform(item: HumanAttentionItem, action: AttentionAction) {
    const key = item.kind + ":" + item.id;
    if (busyRef.current || refreshing) return;
    busyRef.current = key;
    setBusyId(key);
    setError("");
    try {
      const updates: Record<AttentionAction, () => Promise<unknown>> = {
        REQUEST_PROGRESS: () => updateRequestStatus(item.id, "IN_PROGRESS"),
        REQUEST_RESOLVE: () => updateRequestStatus(item.id, "RESOLVED"),
        HANDOFF_ACK: () => acknowledgeHumanHandoff(item.id),
        HANDOFF_RESOLVE: () => resolveHumanHandoff(item.id)
      };
      await updates[action]();
      setConfirmationId(null);
      await Promise.all([
        client.invalidateQueries({ queryKey: ["operations", "attention"] }),
        client.invalidateQueries({ queryKey: ["operations", "requests"] }),
        client.invalidateQueries({ queryKey: ["home", "operations"] })
      ]);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No pudimos actualizar el caso.");
    } finally {
      busyRef.current = null;
      setBusyId(null);
    }
  }

  if (loading) {
    return <div role="status" className={styles.notice}>Consultando casos que requieren intervención…</div>;
  }
  if (failed) {
    return (
      <div className={styles.failure} role="alert">
        <CircleAlert size={18} aria-hidden="true" />
        <span>No pudimos verificar los pendientes. No podemos confirmar que esté todo resuelto.</span>
        <button type="button" onClick={onRefresh}>Reintentar</button>
      </div>
    );
  }
  if (!items?.length) {
    return (
      <div className={styles.calm}>
        <CheckCircle2 size={19} aria-hidden="true" />
        <div>
          <strong>Sin intervenciones pendientes</strong>
          <span>Según la última consulta al backend, no hay casos esperando una acción.</span>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className={styles.meta}>
        <span><Clock3 size={14} aria-hidden="true" /> Casos reales registrados</span>
        <button type="button" className={styles.refresh} onClick={onRefresh} disabled={refreshing || Boolean(busyId)}>
          <RefreshCw size={14} aria-hidden="true" /> {refreshing ? "Actualizando…" : "Actualizar"}
        </button>
      </div>
      <div className={styles.list}>
        {items.map(item => {
          const next = actionFor(item, canManageRequests);
          const itemKey = item.kind + ":" + item.id;
          const ask = confirmationId === itemKey;
          const destructive = next?.action === "REQUEST_RESOLVE" || next?.action === "HANDOFF_RESOLVE";
          return (
            <article className={styles.item} data-kind={item.kind} key={item.kind + "-" + item.id}>
              <span className={styles.icon} aria-hidden="true">
                {item.kind === "HANDOFF" ? <UserRoundCheck size={18} /> : <CircleAlert size={18} />}
              </span>
              <div className={styles.copy}>
                <span className={styles.category}>
                  {item.kind === "HANDOFF" ? "Intervención humana" : "Solicitud"}
                  {" · "}{readableStatus(item)}
                  {["HIGH", "URGENT"].includes(item.priority) ? " · Prioridad alta" : ""}
                </span>
                <strong>{item.title || "Caso sin título"}</strong>
                <time dateTime={item.createdAt}>
                  {item.createdAt && !Number.isNaN(Date.parse(item.createdAt))
                    ? new Intl.DateTimeFormat("es-CL", { dateStyle: "medium", timeStyle: "short" }).format(new Date(item.createdAt))
                    : "Fecha no disponible"}
                </time>
                {item.kind === "HANDOFF" && (
                  <small>El cierre de este escalamiento no cierra una solicitud pendiente.</small>
                )}
              </div>
              {next && (
                <div className={styles.action}
                  role={destructive && ask ? "group" : undefined}
                  aria-label={destructive && ask ? "Confirmación de cierre: " + (item.title || "Caso sin título") : undefined}>
                  {destructive && !ask ? (
                    <button type="button" disabled={Boolean(busyId) || refreshing}
                      onClick={() => setConfirmationId(itemKey)}>{next.label} <ArrowUpRight size={13} /></button>
                  ) : destructive && ask ? (
                    <>
                      <span className={styles.question}>¿Confirmas que gestionaste este caso?</span>
                      <button type="button" disabled={Boolean(busyId) || refreshing}
                        onClick={() => { void perform(item, next.action); }}>
                        {busyId === itemKey ? "Guardando…" : "Confirmar cierre"}
                      </button>
                      <button type="button" className={styles.cancel} disabled={Boolean(busyId) || refreshing}
                        onClick={() => setConfirmationId(null)}>Cancelar</button>
                    </>
                  ) : (
                    <button type="button" disabled={Boolean(busyId) || refreshing}
                      onClick={() => { void perform(item, next.action); }}>
                      {busyId === itemKey ? "Guardando…" : next.label}
                      <ArrowUpRight size={13} aria-hidden="true" />
                    </button>
                  )}
                </div>
              )}
            </article>
          );
        })}
      </div>
      {items.length >= 100 && (
        <p className={styles.notice}>Se muestran hasta 100 casos recientes. La lista no es un total histórico.</p>
      )}
      {error && <p className={styles.failure} role="alert">{error}</p>}
    </>
  );
}
