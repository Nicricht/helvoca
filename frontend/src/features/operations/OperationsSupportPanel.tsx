import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  exportAudit,
  exportCustomers,
  getAudit,
  getRequests,
  updateRequestStatus,
  type AuditFilters,
  type BusinessRequest,
  type RequestStatus
} from "./api";
import styles from "./OperationsSupportPanel.module.css";

interface UserLike {
  roles?: string[];
  permissions?: string[];
}

interface OperationsSupportPanelProps {
  user?: UserLike;
  showCustomerTools: boolean;
}

type PanelView = "REQUESTS" | "AUDIT";

const queryDefaults = {
  retry: false,
  refetchOnWindowFocus: false
} as const;

function canByPermission(user: UserLike | undefined, values: string[], roles: string[]) {
  const permissions = user?.permissions;
  if (Array.isArray(permissions)) {
    return values.some(value => permissions.includes(value));
  }
  return (user?.roles ?? []).some(role => roles.includes(role));
}

function dateTime(value?: string | null) {
  if (!value) return "Sin fecha";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Sin fecha";
  return new Intl.DateTimeFormat("es-CL", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

function requestStatusLabel(value: RequestStatus) {
  return ({
    OPEN: "Abierta",
    IN_PROGRESS: "En progreso",
    RESOLVED: "Resuelta",
    CANCELLED: "Cancelada"
  } as Record<RequestStatus, string>)[value] ?? value;
}

function requestNextStatus(item: BusinessRequest): { status: RequestStatus; label: string } | null {
  if (item.status === "OPEN") {
    return { status: "IN_PROGRESS", label: "Marcar en progreso" };
  }
  if (item.status === "IN_PROGRESS") {
    return { status: "RESOLVED", label: "Marcar resuelta" };
  }
  return null;
}

export function OperationsSupportPanel({
  user,
  showCustomerTools
}: OperationsSupportPanelProps) {
  const canReadRequests = canByPermission(
    user,
    ["REQUESTS_READ", "PERM_REQUESTS_READ", "REQUESTS_MANAGE", "PERM_REQUESTS_MANAGE"],
    ["BUSINESS_ADMIN", "BUSINESS_OWNER", "OPERATOR"]
  );
  const canManageRequests = canByPermission(
    user,
    ["REQUESTS_MANAGE", "PERM_REQUESTS_MANAGE"],
    ["BUSINESS_ADMIN", "BUSINESS_OWNER", "OPERATOR"]
  );
  const canAudit = canByPermission(
    user,
    ["AUDIT_READ", "PERM_AUDIT_READ"],
    ["BUSINESS_ADMIN", "BUSINESS_OWNER"]
  );
  const canExportCustomers = canByPermission(
    user,
    ["CUSTOMERS_EXPORT", "PERM_CUSTOMERS_EXPORT"],
    ["BUSINESS_ADMIN", "BUSINESS_OWNER"]
  );

  const [view, setView] = useState<PanelView>(canReadRequests ? "REQUESTS" : "AUDIT");
  const [requestBusy, setRequestBusy] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const [actor, setActor] = useState("");
  const [action, setAction] = useState("");
  const [resourceType, setResourceType] = useState("");
  const [auditFilters, setAuditFilters] = useState<AuditFilters>({});

  const requests = useQuery({
    queryKey: ["operations", "requests"],
    queryFn: getRequests,
    enabled: canReadRequests && view === "REQUESTS",
    ...queryDefaults
  });

  const audit = useQuery({
    queryKey: ["operations", "audit", auditFilters],
    queryFn: () => getAudit(auditFilters),
    enabled: canAudit && view === "AUDIT",
    ...queryDefaults
  });

  if (!canReadRequests && !canAudit && !(showCustomerTools && canExportCustomers)) {
    return null;
  }

  async function changeRequestStatus(item: BusinessRequest, status: RequestStatus) {
    if (requestBusy) return;
    setRequestBusy(item.id);
    setMessage("");
    try {
      await updateRequestStatus(item.id, status);
      await requests.refetch();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "No pudimos actualizar la solicitud.");
    } finally {
      setRequestBusy(null);
    }
  }

  async function downloadCustomers(format: "csv" | "xlsx") {
    setMessage("");
    try {
      await exportCustomers(format);
      setMessage("Exportación de clientes preparada.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "No pudimos exportar los clientes.");
    }
  }

  async function downloadAudit(format: "csv" | "xlsx") {
    setMessage("");
    try {
      await exportAudit(format, auditFilters);
      setMessage("Exportación de auditoría preparada.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "No pudimos exportar la auditoría.");
    }
  }

  function applyAuditFilters() {
    setAuditFilters({
      actor: actor.trim() || undefined,
      action: action.trim() || undefined,
      resourceType: resourceType.trim() || undefined
    });
  }

  return (
    <section className={styles.panel} aria-labelledby="operationsSupportTitle">
      <header className={styles.header}>
        <div>
          <h2 id="operationsSupportTitle">Historial y auditoría</h2>
          <p>Consulta solicitudes anteriores y registros auditables. Los casos que requieren atención están arriba.</p>
        </div>
        <div className={styles.tabs} aria-label="Historial y auditoría">
          {canReadRequests && (
            <button
              type="button"
              data-active={view === "REQUESTS"}
              onClick={() => setView("REQUESTS")}
            >
              Solicitudes
            </button>
          )}
          {canAudit && (
            <button
              type="button"
              data-active={view === "AUDIT"}
              onClick={() => setView("AUDIT")}
            >
              Auditoría
            </button>
          )}
        </div>
      </header>

      <div className={styles.body}>
        {view === "REQUESTS" && canReadRequests && (
          <>
            {requests.isPending ? (
              <div className={styles.empty} role="status">Cargando solicitudes…</div>
            ) : requests.isError ? (
              <div className={styles.empty} role="alert">No pudimos cargar las solicitudes.</div>
            ) : (requests.data ?? []).length === 0 ? (
              <div className={styles.empty}>No hay solicitudes registradas.</div>
            ) : (
              <div className={styles.requestList}>
                {(requests.data ?? []).map(item => {
                  const next = requestNextStatus(item);
                  return (
                    <article className={styles.requestRow} key={item.id}>
                      <div className={styles.requestMain}>
                        <strong>{item.title || item.requestType || "Solicitud"}</strong>
                        <span>{item.description || item.contactName || "Sin detalle"}</span>
                        {item.contactName && <span>{item.contactName}{item.contactPhone ? " · " + item.contactPhone : ""}</span>}
                        <span>{dateTime(item.createdAt)} · {item.priority || "NORMAL"}</span>
                      </div>
                      <div className={styles.requestActions}>
                        <span className={styles.status}>{requestStatusLabel(item.status)}</span>
                        {canManageRequests && next && (
                          <button
                            className={styles.statusButton}
                            type="button"
                            disabled={Boolean(requestBusy)}
                            onClick={() => changeRequestStatus(item, next.status)}
                          >
                            {requestBusy === item.id ? "Guardando…" : next.label}
                          </button>
                        )}
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </>
        )}

        {view === "AUDIT" && canAudit && (
          <>
            <div className={styles.auditFilters}>
              <input
                aria-label="Actor de auditoría"
                placeholder="Actor"
                value={actor}
                onChange={event => setActor(event.currentTarget.value)}
              />
              <input
                aria-label="Acción de auditoría"
                placeholder="Acción"
                value={action}
                onChange={event => setAction(event.currentTarget.value)}
              />
              <input
                aria-label="Recurso de auditoría"
                placeholder="Recurso"
                value={resourceType}
                onChange={event => setResourceType(event.currentTarget.value)}
              />
              <button className={styles.statusButton} type="button" onClick={applyAuditFilters}>
                Aplicar filtros
              </button>
            </div>

            {audit.isPending ? (
              <div className={styles.empty} role="status">Cargando auditoría…</div>
            ) : audit.isError ? (
              <div className={styles.empty} role="alert">No pudimos cargar la auditoría.</div>
            ) : (audit.data ?? []).length === 0 ? (
              <div className={styles.empty}>Todavía no hay eventos de auditoría.</div>
            ) : (
              <div className={styles.auditList}>
                {(audit.data ?? []).map(item => (
                  <article className={styles.auditRow} key={item.id}>
                    <div className={styles.auditMain}>
                      <strong>{item.action || "Evento"}</strong>
                      <span>{item.resourceType || "Recurso"}{item.resourceId ? " · " + item.resourceId : ""}</span>
                      <span>{item.actorName || item.actorType || "Sistema"}{item.actorRole ? " · " + item.actorRole : ""}</span>
                    </div>
                    <time>{dateTime(item.createdAt)}</time>
                  </article>
                ))}
              </div>
            )}

            <div className={styles.actions}>
              <button type="button" onClick={() => downloadAudit("csv")}>Exportar auditoría CSV</button>
              <button type="button" onClick={() => downloadAudit("xlsx")}>Exportar auditoría XLSX</button>
            </div>
          </>
        )}

        {message && <p className={styles.message} role="status">{message}</p>}
      </div>

      {showCustomerTools && canExportCustomers && (
        <footer className={styles.tools} aria-label="Herramientas de clientes">
          <button type="button" onClick={() => downloadCustomers("csv")}>Exportar CSV</button>
          <button type="button" onClick={() => downloadCustomers("xlsx")}>Exportar XLSX</button>
        </footer>
      )}
    </section>
  );
}
