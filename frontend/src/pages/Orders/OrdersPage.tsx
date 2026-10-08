import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent
} from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import {
  ArrowRight,
  CalendarClock,
  ChevronRight,
  Clock3,
  MessageSquareText,
  FileText,
  PackageCheck,
  Search,
  RefreshCw,
  ShoppingBag,
  Truck,
  UserRound,
  UsersRound,
  X
} from "lucide-react";
import { ApiError } from "../../api/client";
import { AppShell } from "../../components/AppShell/AppShell";
import { OperationsSupportPanel } from "../../features/operations/OperationsSupportPanel";
import { OperationsQuotesPanel } from "../../features/operations/OperationsQuotesPanel";
import {
  getCallContext,
  getConversation,
  getOperationEvents,
  updateOrderStatus,
  updatePreparationStatus,
  type BusinessOrder,
  type ConversationContext,
  type OperationEvent,
  type OrderSource,
  type OrderStatus
} from "../../features/orders/api";
import { useOrdersWorkspace } from "../../features/orders/useOrdersWorkspace";
import styles from "./OrdersPage.module.css";

type StatusFilter = "ALL" | "ACTIVE" | OrderStatus;
type SourceFilter = "ALL" | OrderSource;
type SortMode = "NEWEST" | "OLDEST" | "STATUS";
type EmbeddedView = "ORDERS" | "CUSTOMERS" | "CONVERSATIONS" | "QUOTES";

interface ContextState {
  loading: boolean;
  partial: boolean;
  events: OperationEvent[];
  conversation: ConversationContext | null;
  callSummary: string;
}

const EMPTY_CONTEXT: ContextState = {
  loading: false,
  partial: false,
  events: [],
  conversation: null,
  callSummary: ""
};

const statusLabels: Record<OrderStatus, string> = {
  CONFIRMED: "Confirmado",
  PREPARING: "Preparando",
  READY: "Listo",
  DISPATCHED: "Despachado",
  COMPLETED: "Completado",
  CANCELLED: "Cancelado"
};

const sourceLabels: Record<OrderSource, string> = {
  VOICE: "Voz",
  WHATSAPP: "WhatsApp",
  MANUAL: "Manual",
  API: "API"
};

function money(value: number, currency = "CLP") {
  try {
    return new Intl.NumberFormat("es-CL", {
      style: "currency",
      currency,
      maximumFractionDigits: currency === "CLP" ? 0 : 2
    }).format(Number(value || 0));
  } catch {
    return `${Number(value || 0).toLocaleString("es-CL")} ${currency}`;
  }
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

// This is a display reference, not a business folio. Full UUID remains authoritative.
function orderReference(id: string) {
  return id.length > 20 ? `${id.slice(0, 8)}…${id.slice(-6)}` : id;
}

function fulfillmentLabel(order: BusinessOrder) {
  return order.fulfillmentType === "DELIVERY" ? "Delivery" : "Retiro";
}

function statusClass(status: OrderStatus) {
  if (status === "COMPLETED") return styles.statusSuccess;
  if (status === "CANCELLED") return styles.statusDanger;
  if (status === "READY") return styles.statusReady;
  if (status === "PREPARING" || status === "DISPATCHED") return styles.statusInfo;
  return styles.statusWarning;
}

function nextActions(order: BusinessOrder, canManage: boolean, canPrepare: boolean) {
  const actions: Array<{ status: OrderStatus; label: string; tone: "primary" | "danger" | "default" }> = [];

  if (order.status === "CONFIRMED" && (canManage || canPrepare)) {
    actions.push({ status: "PREPARING", label: "Empezar preparación", tone: "primary" });
  }
  if (order.status === "PREPARING" && (canManage || canPrepare)) {
    actions.push({ status: "READY", label: "Marcar como listo", tone: "primary" });
  }
  if (order.status === "READY" && canManage) {
    actions.push({
      status: order.fulfillmentType === "DELIVERY" ? "DISPATCHED" : "COMPLETED",
      label: order.fulfillmentType === "DELIVERY" ? "Despachar pedido" : "Completar retiro",
      tone: "primary"
    });
  }
  if (order.status === "DISPATCHED" && canManage) {
    actions.push({ status: "COMPLETED", label: "Completar entrega", tone: "primary" });
  }
  if (
    canManage
    && (order.status === "CONFIRMED" || order.status === "PREPARING")
  ) {
    actions.push({ status: "CANCELLED", label: "Cancelar pedido", tone: "danger" });
  }

  return actions;
}

function mutationMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return "El pedido cambió o existe un conflicto de estado. Actualiza los datos e intenta nuevamente.";
    }
    if (error.status === 400) {
      return "Ese cambio no es válido para el estado actual del pedido.";
    }
    if (error.status === 403) {
      return "Tu rol no tiene permiso para realizar ese cambio.";
    }
    if (error.status >= 500) {
      return "No pudimos actualizar el pedido. El servidor no completó el cambio.";
    }
  }
  return error instanceof Error && error.message
    ? error.message
    : "No pudimos actualizar el pedido.";
}

function orderSearchText(order: BusinessOrder) {
  return [
    order.id,
    order.operationId,
    order.contactName,
    order.contactPhone,
    order.source,
    sourceLabels[order.source],
    order.status,
    statusLabels[order.status],
    order.fulfillmentType,
    fulfillmentLabel(order),
    ...(order.lines ?? []).map(line => line.name)
  ]
    .filter(Boolean)
    .join(" ")
    .toLocaleLowerCase("es");
}

function openOnKeyboard(event: KeyboardEvent<HTMLElement>, action: () => void) {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    action();
  }
}

export function OrdersPage() {
  const reduceMotion = useReducedMotion();
  const model = useOrdersWorkspace();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [source, setSource] = useState<SourceFilter>("ALL");
  const [sort, setSort] = useState<SortMode>("NEWEST");
  const [view, setView] = useState<EmbeddedView>("ORDERS");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [context, setContext] = useState<ContextState>(EMPTY_CONTEXT);
  const [mutationPending, setMutationPending] = useState(false);
  const [mutationError, setMutationError] = useState("");
  const mutationLock = useRef(false);

  const orders = model.orders.data ?? [];
  const quoteOnly = !model.canReadOrders && model.canReadQuotes;
  const activeView: EmbeddedView = quoteOnly ? "QUOTES" : view;
  const selected = selectedId
    ? orders.find(order => order.id === selectedId) ?? null
    : null;

  const visibleOrders = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("es");
    const filtered = orders.filter(order => {
      if (query && !orderSearchText(order).includes(query)) return false;
      if (status === "ACTIVE" && ["COMPLETED", "CANCELLED"].includes(order.status)) return false;
      if (status !== "ALL" && status !== "ACTIVE" && order.status !== status) return false;
      if (source !== "ALL" && order.source !== source) return false;
      if (
        activeView === "CONVERSATIONS"
        && (!order.sourceReferenceId || (order.source !== "WHATSAPP" && order.source !== "VOICE"))
      ) {
        return false;
      }
      return true;
    });

    if (sort === "OLDEST") {
      return [...filtered].sort(
        (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
      );
    }
    if (sort === "STATUS") {
      return [...filtered].sort(
        (a, b) => statusLabels[a.status].localeCompare(statusLabels[b.status], "es")
      );
    }
    return [...filtered].sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    );
  }, [orders, search, status, source, sort, activeView]);

  const customerGroups = useMemo(() => {
    const groups = new Map<string, BusinessOrder[]>();
    for (const order of visibleOrders) {
      const key = order.contactPhone || order.contactName || order.id;
      const next = groups.get(key) ?? [];
      next.push(order);
      groups.set(key, next);
    }
    return [...groups.entries()].map(([key, customerOrders]) => ({
      key,
      name: customerOrders[0]?.contactName || "Cliente sin nombre",
      phone: customerOrders[0]?.contactPhone || "Sin teléfono",
      orders: customerOrders,
      active: customerOrders.filter(order =>
        !["COMPLETED", "CANCELLED"].includes(order.status)
      ).length,
      total: customerOrders.reduce((sum, order) => sum + Number(order.total || 0), 0)
    }));
  }, [visibleOrders]);

  const activeCount = orders.filter(order =>
    !["COMPLETED", "CANCELLED"].includes(order.status)
  ).length;
  const preparingCount = orders.filter(order => order.status === "PREPARING").length;
  const readyCount = orders.filter(order => order.status === "READY").length;

  useEffect(() => {
    if (!selected) {
      setContext(EMPTY_CONTEXT);
      return;
    }

    let cancelled = false;
    const tasks: Array<Promise<unknown>> = [];
    let eventTaskIndex = -1;
    let sourceTaskIndex = -1;

    if (selected.operationId) {
      eventTaskIndex = tasks.length;
      tasks.push(getOperationEvents(selected.operationId));
    }

    if (selected.sourceReferenceId && model.canReadConversations) {
      sourceTaskIndex = tasks.length;
      if (selected.source === "WHATSAPP") {
        tasks.push(getConversation(selected.sourceReferenceId));
      } else if (selected.source === "VOICE") {
        tasks.push(getCallContext(selected.sourceReferenceId));
      }
    }

    if (tasks.length === 0) {
      setContext(EMPTY_CONTEXT);
      return;
    }

    setContext({
      loading: true,
      partial: false,
      events: [],
      conversation: null,
      callSummary: ""
    });

    Promise.allSettled(tasks).then(results => {
      if (cancelled) return;

      let partial = false;
      let events: OperationEvent[] = [];
      let conversation: ConversationContext | null = null;
      let callSummary = "";

      if (eventTaskIndex >= 0) {
        const eventResult = results[eventTaskIndex];
        if (eventResult.status === "fulfilled") {
          events = eventResult.value as OperationEvent[];
        } else {
          partial = true;
        }
      }

      if (sourceTaskIndex >= 0) {
        const sourceResult = results[sourceTaskIndex];
        if (sourceResult.status === "fulfilled") {
          if (selected.source === "WHATSAPP") {
            conversation = sourceResult.value as ConversationContext;
          } else {
            const call = sourceResult.value as {
              summary?: string | null;
              transcript?: string | null;
            };
            callSummary = call.summary || call.transcript || "";
          }
        } else {
          partial = true;
        }
      }

      setContext({
        loading: false,
        partial,
        events,
        conversation,
        callSummary
      });
    });

    return () => {
      cancelled = true;
    };
  }, [
    selectedId,
    selected?.operationId,
    selected?.source,
    selected?.sourceReferenceId,
    model.canReadConversations
  ]);

  async function changeStatus(nextStatus: OrderStatus) {
    if (!selected || mutationLock.current) return;
    if (!model.canManage && !model.canPrepare) return;

    mutationLock.current = true;
    setMutationPending(true);
    setMutationError("");

    try {
      if (!model.canManage && model.canPrepare) {
        await updatePreparationStatus(selected.id, nextStatus);
      } else {
        await updateOrderStatus(selected.id, nextStatus);
      }
      await model.refetchOrders();
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      mutationLock.current = false;
      setMutationPending(false);
    }
  }

  function resetFilters() {
    setSearch("");
    setStatus("ALL");
    setSource("ALL");
    setSort("NEWEST");
  }

  const hasFilters = Boolean(search.trim()) || status !== "ALL" || source !== "ALL";
  const loading = model.me.isPending || (model.canReadOrders && model.orders.isPending);
  const failed = model.me.isError || (model.canReadOrders && model.orders.isError && !model.orders.data);

  if (loading) {
    return (
      <AppShell>
        <main className="rv-page-frame">
          <header className="rv-page-header">
            <div>
              <p className="eyebrow">OPERACIONES</p>
              <h1>Pedidos</h1>
              <p>Gestiona el trabajo comercial desde que entra hasta que se completa.</p>
            </div>
          </header>
          <div className={styles.loadingCard} role="status" aria-live="polite">
            <span className={styles.spinner} aria-hidden="true" />
            <span>Cargando pedidos…</span>
          </div>
        </main>
      </AppShell>
    );
  }

  if (failed) {
    return (
      <AppShell>
        <main className="rv-page-frame">
          <header className="rv-page-header">
            <div>
              <p className="eyebrow">OPERACIONES</p>
              <h1>Pedidos</h1>
              <p>Gestiona el trabajo comercial desde que entra hasta que se completa.</p>
            </div>
          </header>
          <section className={styles.errorCard} role="alert">
            <strong>No pudimos cargar los pedidos.</strong>
            <p>La operación no se mostrará como vacía si el servidor no respondió.</p>
            <button className="button primary" type="button" onClick={() => { void (model.me.isError ? model.me.refetch() : model.orders.refetch()); }}>
              Reintentar
            </button>
          </section>
        </main>
      </AppShell>
    );
  }

  if (!model.canReadOrders && !model.canReadQuotes) {
    return (
      <AppShell>
        <main className="rv-page-frame" data-visual-page="operations">
          <header className="rv-page-header">
            <div>
              <p className="eyebrow">OPERACIONES</p>
              <h1>Pedidos</h1>
            </div>
          </header>
          <section className={styles.errorCard} role="alert">
            <strong>No tienes permiso para consultar pedidos ni cotizaciones.</strong>
            <p>Solicita acceso a una persona administradora de tu negocio.</p>
          </section>
        </main>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <main className={`rv-page-frame ${styles.page}`} data-visual-page="operations">
        <header className="rv-page-header">
          <div>
            <p className="eyebrow">OPERACIONES</p>
            <h1>Pedidos</h1>
            <p>
              {quoteOnly ? "Gestiona las cotizaciones del negocio según los permisos de tu rol." : "Gestiona pedidos, preparación, entregas y cotizaciones autorizadas sin salir del flujo."}
            </p>
          </div>
          <span className={styles.permissionPill}>
            {model.canManage || model.canPrepare || model.canManageQuotes ? "Gestión habilitada" : "Solo lectura"}
          </span>
        </header>

        {model.canReadOrders && <section className={styles.operationalBar} aria-label="Actualización de pedidos">
          <div className={styles.operationalBarCopy}>
            <span className={styles.syncLabel}><span className={styles.syncDot} aria-hidden="true" /> Consulta bajo demanda</span>
            <p>
              Mostrando los últimos pedidos disponibles (máximo 100).
              {model.orders.dataUpdatedAt > 0 && (
                <> Última consulta: {new Intl.DateTimeFormat("es-CL", { timeStyle: "short" }).format(model.orders.dataUpdatedAt)}.</>
              )}
            </p>
            {model.orders.isRefetchError && (
              <p className={styles.syncError} role="alert">
                No pudimos actualizar los pedidos. Se mantienen los últimos datos disponibles.
              </p>
            )}
          </div>
          <button
            className={styles.refreshButton}
            type="button"
            disabled={model.orders.isFetching}
            onClick={() => { void model.orders.refetch(); }}
          >
            <RefreshCw size={15} aria-hidden="true" />
            {model.orders.isFetching ? "Actualizando…" : "Actualizar pedidos"}
          </button>
          <img
            className={styles.operationalRobot}
            src="/app/assets/recepvoz/v2/operations/hero-order-robot.webp"
            alt=""
            aria-hidden="true"
          />
        </section>}

        {model.canReadOrders && <section className={styles.summaryGrid} aria-label="Resumen de pedidos">
          <button type="button" className={styles.summaryCard} aria-label="Mostrar pedidos activos" aria-pressed={status === "ACTIVE" && view === "ORDERS"} onClick={() => { setStatus("ACTIVE"); setView("ORDERS"); }}>
            <span className={styles.summaryIcon}><ShoppingBag size={18} aria-hidden="true" /></span>
            <div><span>Activos</span><strong>{activeCount}</strong><small>de los más recientes</small></div>
          </button>
          <button type="button" className={styles.summaryCard} aria-label="Mostrar pedidos preparando" aria-pressed={status === "PREPARING" && view === "ORDERS"} onClick={() => { setStatus("PREPARING"); setView("ORDERS"); }}>
            <span className={styles.summaryIcon}><Clock3 size={18} aria-hidden="true" /></span>
            <div><span>Preparando</span><strong>{preparingCount}</strong><small>de los más recientes</small></div>
          </button>
          <button type="button" className={styles.summaryCard} aria-label="Mostrar pedidos listos" aria-pressed={status === "READY" && view === "ORDERS"} onClick={() => { setStatus("READY"); setView("ORDERS"); }}>
            <span className={styles.summaryIcon}><PackageCheck size={18} aria-hidden="true" /></span>
            <div><span>Listos</span><strong>{readyCount}</strong><small>por retirar o despachar</small></div>
          </button>
        </section>}

        <section className={styles.workspace} aria-labelledby="ordersWorkspaceTitle">
          <div className={styles.workspaceHeader}>
            <div>
              <h2 id="ordersWorkspaceTitle">{quoteOnly ? "Cotizaciones" : "Flujo operativo"}</h2>
              <p>Pedidos como actividad principal, con clientes, conversaciones y cotizaciones en contexto.</p>
            </div>
            <div className={styles.viewSwitch} aria-label="Vista de operaciones">
              {model.canReadOrders && <button
                type="button"
                data-active={activeView === "ORDERS"}
                onClick={() => setView("ORDERS")}
              >
                <ShoppingBag size={15} aria-hidden="true" />Pedidos
              </button>}
              {model.canReadOrders && <button
                type="button"
                data-active={activeView === "CUSTOMERS"}
                onClick={() => setView("CUSTOMERS")}
              >
                <UsersRound size={15} aria-hidden="true" />Clientes
              </button>}
              {model.canReadOrders && <button
                type="button"
                data-active={activeView === "CONVERSATIONS"}
                onClick={() => setView("CONVERSATIONS")}
              >
                <MessageSquareText size={15} aria-hidden="true" />Conversaciones
              </button>}
              {model.canReadQuotes && <button
                type="button"
                data-active={activeView === "QUOTES"}
                onClick={() => setView("QUOTES")}
              >
                <FileText size={15} aria-hidden="true" />Cotizaciones
              </button>}
            </div>
          </div>

          {activeView !== "QUOTES" && <div className={styles.toolbar}>
            <label className={styles.searchField}>
              <Search size={17} aria-hidden="true" />
              <span className={styles.visuallyHidden}>Buscar pedidos</span>
              <input
                type="search"
                aria-label="Buscar pedidos"
                placeholder="Buscar cliente, pedido, producto…"
                value={search}
                onChange={event => setSearch(event.target.value)}
              />
            </label>

            <label className={styles.selectField}>
              <span>Estado</span>
              <select
                aria-label="Estado"
                value={status}
                onChange={event => setStatus(event.target.value as StatusFilter)}
              >
                <option value="ALL">Todos</option>
                <option value="ACTIVE">Activos</option>
                <option value="CONFIRMED">Confirmados</option>
                <option value="PREPARING">Preparando</option>
                <option value="READY">Listos</option>
                <option value="DISPATCHED">Despachados</option>
                <option value="COMPLETED">Completados</option>
                <option value="CANCELLED">Cancelados</option>
              </select>
            </label>

            <label className={styles.selectField}>
              <span>Origen</span>
              <select
                aria-label="Origen"
                value={source}
                onChange={event => setSource(event.target.value as SourceFilter)}
              >
                <option value="ALL">Todos</option>
                <option value="WHATSAPP">WhatsApp</option>
                <option value="VOICE">Voz</option>
                <option value="MANUAL">Manual</option>
                <option value="API">API</option>
              </select>
            </label>

            <label className={styles.selectField}>
              <span>Orden</span>
              <select
                aria-label="Orden"
                value={sort}
                onChange={event => setSort(event.target.value as SortMode)}
              >
                <option value="NEWEST">Más recientes</option>
                <option value="OLDEST">Más antiguos</option>
                <option value="STATUS">Por estado</option>
              </select>
            </label>
          </div>}

          {activeView === "QUOTES" ? (
            <OperationsQuotesPanel canManage={model.canManageQuotes} />
          ) : activeView === "CUSTOMERS" ? (
            <div className={styles.customerGrid} data-testid="orders-customer-view">
              {customerGroups.length === 0 ? (
                <div className={styles.emptyState}>
                  <strong>No hay clientes que coincidan.</strong>
                  <span>Ajusta los filtros para ampliar los resultados.</span>
                </div>
              ) : customerGroups.map(group => (
                <button
                  type="button"
                  className={styles.customerCard}
                  key={group.key}
                  onClick={() => setSelectedId(group.orders[0].id)}
                >
                  <span className={styles.customerAvatar}><UserRound size={18} aria-hidden="true" /></span>
                  <span className={styles.customerMain}>
                    <strong>{group.name}</strong>
                    <small>{group.phone}</small>
                  </span>
                  <span className={styles.customerMetrics}>
                    <small>{group.orders.length} pedidos · {group.active} activos</small>
                    <strong>{money(group.total, group.orders[0]?.currency || "CLP")}</strong>
                  </span>
                  <ChevronRight size={18} aria-hidden="true" />
                </button>
              ))}
            </div>
          ) : (
            <>
              {orders.length === 0 ? (
                <div className={styles.emptyState}>
                  <strong>Todavía no hay pedidos.</strong>
                  <span>Cuando entre el primer pedido aparecerá aquí.</span>
                </div>
              ) : visibleOrders.length === 0 ? (
                <div className={styles.emptyState}>
                  <strong>No encontramos coincidencias.</strong>
                  <span>Prueba con otros filtros o términos de búsqueda.</span>
                  {hasFilters && (
                    <button className="button ghost" type="button" onClick={resetFilters}>
                      Limpiar filtros
                    </button>
                  )}
                </div>
              ) : (
                <>
                  <div className={styles.tableScroll}>
                    <table className={styles.table}>
                      <thead>
                        <tr>
                          <th>Pedido</th>
                          <th>Cliente</th>
                          <th>Entrega</th>
                          <th>Origen</th>
                          <th>Estado</th>
                          <th>Total</th>
                          <th>Creado</th>
                        </tr>
                      </thead>
                      <tbody>
                        {visibleOrders.map((order, index) => (
                          <motion.tr
                            key={order.id}
                            data-testid={`orders-row-${order.id}`}
                            tabIndex={0}
                            role="button"
                            aria-label={`Abrir pedido ${order.id} de ${order.contactName || "cliente"}`}
                            onClick={() => setSelectedId(order.id)}
                            onKeyDown={event => openOnKeyboard(event, () => setSelectedId(order.id))}
                            initial={reduceMotion ? false : { opacity: 0, y: 6 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ duration: reduceMotion ? 0 : .2, delay: reduceMotion ? 0 : Math.min(index * .025, .16) }}
                          >
                            <td><strong className={styles.orderId} title={order.id}>Ref. {orderReference(order.id)}</strong></td>
                            <td>
                              <span className={styles.customerCell}>
                                <strong>{order.contactName || "Cliente"}</strong>
                                <small>{order.contactPhone || "Sin teléfono"}</small>
                              </span>
                            </td>
                            <td>{fulfillmentLabel(order)}</td>
                            <td><span className={styles.sourcePill}>{sourceLabels[order.source]}</span></td>
                            <td><span className={statusClass(order.status)}>{statusLabels[order.status]}</span></td>
                            <td><strong className={styles.money}>{money(order.total, order.currency)}</strong></td>
                            <td>{dateTime(order.createdAt)}</td>
                          </motion.tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  <div className={styles.mobileList} data-testid="orders-mobile-list">
                    {visibleOrders.map((order, index) => (
                      <motion.button
                        type="button"
                        key={order.id}
                        className={styles.mobileOrder}
                        data-testid={`orders-mobile-order-${order.id}`}
                        onClick={() => setSelectedId(order.id)}
                        initial={reduceMotion ? false : { opacity: 0, y: 8 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: reduceMotion ? 0 : .2, delay: reduceMotion ? 0 : Math.min(index * .025, .14) }}
                      >
                        <span className={styles.mobileOrderTop}>
                          <strong>{order.contactName || "Cliente"}</strong>
                          <span className={statusClass(order.status)}>{statusLabels[order.status]}</span>
                        </span>
                        <span className={styles.mobileOrderMeta}>
                          <span title={order.id}>Ref. {orderReference(order.id)}</span>
                          <span>{fulfillmentLabel(order)}</span>
                          <span>{sourceLabels[order.source]}</span>
                        </span>
                        <span className={styles.mobileOrderBottom}>
                          <strong>{money(order.total, order.currency)}</strong>
                          <ArrowRight size={17} aria-hidden="true" />
                        </span>
                      </motion.button>
                    ))}
                  </div>
                </>
              )}
            </>
          )}
        </section>

        <OperationsSupportPanel
          user={model.me.data}
          showCustomerTools={activeView === "CUSTOMERS"}
        />

        <AnimatePresence>
          {selected && (
            <>
              <motion.button
                type="button"
                className={styles.drawerBackdrop}
                aria-label="Cerrar detalle"
                onClick={() => setSelectedId(null)}
                initial={reduceMotion ? false : { opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: reduceMotion ? 0 : .18 }}
              />
              <motion.aside
                className={styles.drawer}
                role="dialog"
                aria-modal="true"
                aria-label={`Pedido ${selected.id} · ${selected.contactName || "Cliente"}`}
                initial={reduceMotion ? false : { opacity: 0, clipPath: "inset(0 0 0 8%)" }}
                animate={{ opacity: 1, clipPath: "inset(0 0 0 0%)" }}
                exit={{ opacity: 0, clipPath: "inset(0 0 0 6%)" }}
                transition={{ duration: reduceMotion ? 0 : .24, ease: "easeOut" }}
              >
                <header className={styles.drawerHeader}>
                  <div>
                    <span className={styles.drawerEyebrow} title={selected.id}>Ref. {orderReference(selected.id)}</span>
                    <h2>{selected.contactName || "Cliente"}</h2>
                    <div className={styles.drawerBadges}>
                      <span className={statusClass(selected.status)}>{statusLabels[selected.status]}</span>
                      <span className={styles.sourcePill}>{sourceLabels[selected.source]}</span>
                      <span className={styles.fulfillmentPill}>{fulfillmentLabel(selected)}</span>
                    </div>
                  </div>
                  <button
                    type="button"
                    className={styles.closeButton}
                    aria-label="Cerrar pedido"
                    onClick={() => setSelectedId(null)}
                  >
                    <X size={19} aria-hidden="true" />
                  </button>
                </header>

                <div className={styles.drawerBody}>
                  {mutationError && (
                    <div className={styles.mutationError} role="alert">{mutationError}</div>
                  )}

                  <section className={styles.detailSection} aria-labelledby="orderCustomerTitle">
                    <div className={styles.sectionTitle}>
                      <UserRound size={16} aria-hidden="true" />
                      <h3 id="orderCustomerTitle">Cliente y entrega</h3>
                    </div>
                    <dl className={styles.factGrid}>
                      <div><dt>Cliente</dt><dd>{selected.contactName || "Sin nombre"}</dd></div>
                      <div><dt>Teléfono</dt><dd>{selected.contactPhone || "Sin teléfono"}</dd></div>
                      <div><dt>Entrega</dt><dd>{fulfillmentLabel(selected)}</dd></div>
                      <div><dt>Origen</dt><dd>{sourceLabels[selected.source]}</dd></div>
                      <div><dt>Estado</dt><dd>{statusLabels[selected.status]}</dd></div>
                      <div><dt>Creado</dt><dd>{dateTime(selected.createdAt)}</dd></div>
                    </dl>
                    {selected.fulfillmentType === "DELIVERY" && (
                      <div className={styles.address}>
                        <Truck size={16} aria-hidden="true" />
                        <span>{selected.deliveryAddress || "Dirección pendiente"}</span>
                      </div>
                    )}
                  </section>

                  <section className={styles.detailSection} aria-labelledby="orderLinesTitle">
                    <div className={styles.sectionTitle}>
                      <ShoppingBag size={16} aria-hidden="true" />
                      <h3 id="orderLinesTitle">Productos</h3>
                    </div>
                    <div className={styles.lineList}>
                      {(selected.lines ?? []).map((line, index) => (
                        <div className={styles.lineItem} key={`${line.catalogItemId || line.name}-${index}`}>
                          <div>
                            <strong>{line.name}</strong>
                            <span>{line.quantity} × {money(line.unitPrice, selected.currency)}</span>
                            {line.notes && <small>{line.notes}</small>}
                          </div>
                          <strong>{money(line.lineTotal, selected.currency)}</strong>
                        </div>
                      ))}
                    </div>
                    <dl className={styles.totals}>
                      <div><dt>Subtotal</dt><dd>{money(selected.subtotal, selected.currency)}</dd></div>
                      <div><dt>Despacho</dt><dd>{money(selected.deliveryFee, selected.currency)}</dd></div>
                      <div className={styles.totalRow}><dt>Total</dt><dd>{money(selected.total, selected.currency)}</dd></div>
                    </dl>
                  </section>

                  <section className={styles.detailSection} aria-labelledby="conversationTitle">
                    <div className={styles.sectionTitle}>
                      <MessageSquareText size={16} aria-hidden="true" />
                      <h3 id="conversationTitle">Conversación contextual</h3>
                    </div>

                    {context.loading && (
                      <div className={styles.contextStatus} role="status">
                        Cargando contexto de conversación e historial…
                      </div>
                    )}
                    {!context.loading && context.partial && (
                      <div className={styles.contextStatus} role="status">
                        Parte del contexto, historial o conversación no está disponible ahora. El pedido sigue operativo.
                      </div>
                    )}

                    {!model.canReadConversations && selected.sourceReferenceId && (
                      <p className={styles.contextMuted}>Tu rol no puede consultar la conversación vinculada.</p>
                    )}

                    {(context.conversation?.messages ?? []).length > 0 && (
                      <div className={styles.messageList}>
                        {(context.conversation?.messages ?? []).map(message => (
                          <article
                            className={message.direction === "OUTBOUND" ? styles.messageOutbound : styles.messageInbound}
                            key={message.id}
                          >
                            <span>{message.direction === "OUTBOUND" ? "Helvoca" : "Cliente"}</span>
                            <p>{message.content || "Mensaje sin contenido"}</p>
                          </article>
                        ))}
                      </div>
                    )}

                    {context.callSummary && (
                      <div className={styles.callSummary}>
                        <strong>Resumen de llamada</strong>
                        <p>{context.callSummary}</p>
                      </div>
                    )}

                    {!context.loading
                      && !context.partial
                      && !context.callSummary
                      && (context.conversation?.messages ?? []).length === 0
                      && (
                        <p className={styles.contextMuted}>No hay conversación visible asociada a este pedido.</p>
                      )}
                  </section>

                  <section className={styles.detailSection} aria-labelledby="historyTitle">
                    <div className={styles.sectionTitle}>
                      <CalendarClock size={16} aria-hidden="true" />
                      <h3 id="historyTitle">Historial</h3>
                    </div>
                    {context.events.length > 0 ? (
                      <ol className={styles.timeline}>
                        {context.events.map(event => (
                          <li key={event.id}>
                            <span className={styles.timelineDot} aria-hidden="true" />
                            <div>
                              <strong>{event.eventType}</strong>
                              <small>
                                {[event.actorType, event.channel, dateTime(event.createdAt)]
                                  .filter(Boolean)
                                  .join(" · ")}
                              </small>
                            </div>
                          </li>
                        ))}
                      </ol>
                    ) : !context.loading && !context.partial ? (
                      <p className={styles.contextMuted}>Todavía no hay eventos visibles.</p>
                    ) : null}
                  </section>
                </div>

                {nextActions(selected, model.canManage, model.canPrepare).length > 0 && (
                  <footer className={styles.drawerFooter}>
                    {nextActions(selected, model.canManage, model.canPrepare).map(action => (
                      <button
                        type="button"
                        key={action.status}
                        className={
                          action.tone === "primary"
                            ? "button primary"
                            : action.tone === "danger"
                              ? `button ghost ${styles.dangerAction}`
                              : "button ghost"
                        }
                        disabled={mutationPending}
                        onClick={() => changeStatus(action.status)}
                      >
                        {mutationPending ? "Actualizando…" : action.label}
                      </button>
                    ))}
                  </footer>
                )}
              </motion.aside>
            </>
          )}
        </AnimatePresence>
      </main>
    </AppShell>
  );
}
