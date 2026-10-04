import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AppShell } from "../../components/AppShell/AppShell";
import { CustomerQuickCreate } from "../../features/agenda/CustomerQuickCreate";
import { ConversationPanel } from "../../features/conversations";
import {
  cancelBooking,
  checkAvailability,
  createBooking,
  getBookingActivity,
  getBookingContext,
  getBookings,
  getCurrentUser,
  getCustomers,
  getPublicBookingState,
  getServices,
  rescheduleBooking,
  updatePublicBookingState,
  type Booking,
  type Customer,
  type ServiceItem
} from "../../features/agenda/api";
import styles from "./AgendaPage.module.css";

type DetailTab = "summary" | "conversation" | "customer" | "activity";
type AvailabilityState = "idle" | "checking" | "available" | "unavailable" | "error";

const queryDefaults = {
  retry: false,
  refetchOnWindowFocus: false
} as const;

const STATUS_LABELS: Record<string, string> = {
  CONFIRMED: "Confirmada",
  PENDING: "Pendiente",
  CANCELLED: "Cancelada",
  CANCELED: "Cancelada",
  COMPLETED: "Completada",
  NO_SHOW: "No asistió"
};

const SOURCE_LABELS: Record<string, string> = {
  AI_CALL: "Voz",
  AI_WHATSAPP: "WhatsApp",
  ADMIN: "Manual",
  PUBLIC_WEB: "Web"
};

const ACTIVITY_LABELS: Record<string, string> = {
  BOOKING_CREATE: "Reserva creada",
  BOOKING_CREATED: "Reserva creada",
  BOOKING_RESCHEDULE: "Reserva reprogramada",
  BOOKING_RESCHEDULED: "Reserva reprogramada",
  BOOKING_CANCEL: "Reserva cancelada",
  BOOKING_CANCELLED: "Reserva cancelada"
};

function statusLabel(value?: string | null) {
  return STATUS_LABELS[String(value ?? "").toUpperCase()] ?? value ?? "Sin estado";
}

function sourceLabel(value?: string | null) {
  return SOURCE_LABELS[String(value ?? "").toUpperCase()] ?? value ?? "Sin origen";
}

function activityLabel(value?: string | null) {
  if (!value) return "Actividad registrada";
  return ACTIVITY_LABELS[value] ?? value
    .toLowerCase()
    .split("_")
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function formatDateTime(value?: string | null) {
  if (!value) return "Sin fecha";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Sin fecha";
  return new Intl.DateTimeFormat("es-CL", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

function toIso(date: string, time: string) {
  const parsed = new Date(date + "T" + time + ":00");
  if (Number.isNaN(parsed.getTime())) {
    throw new Error("Fecha u hora inválida.");
  }
  return parsed.toISOString();
}

function datePart(value: string) {
  return new Date(value).toISOString().slice(0, 10);
}

function timePart(value: string) {
  return new Date(value).toISOString().slice(11, 16);
}

function customerFor(book: Booking | null, customers: Customer[]) {
  return book ? customers.find(customer => customer.id === book.customerId) ?? null : null;
}

function serviceFor(book: Booking | null, services: ServiceItem[]) {
  return book ? services.find(service => service.id === book.serviceId) ?? null : null;
}

export function AgendaPage() {
  const me = useQuery({
    queryKey: ["auth", "me"],
    queryFn: getCurrentUser,
    ...queryDefaults
  });
  const bookings = useQuery({
    queryKey: ["agenda", "bookings"],
    queryFn: getBookings,
    ...queryDefaults
  });
  const customers = useQuery({
    queryKey: ["agenda", "customers"],
    queryFn: getCustomers,
    ...queryDefaults
  });
  const services = useQuery({
    queryKey: ["agenda", "services"],
    queryFn: getServices,
    ...queryDefaults
  });

  const [search, setSearch] = useState("");
  const [serviceFilter, setServiceFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState("all");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detailTab, setDetailTab] = useState<DetailTab>("summary");

  const [createOpen, setCreateOpen] = useState(false);
  const [createCustomerId, setCreateCustomerId] = useState("");
  const [createServiceId, setCreateServiceId] = useState("");
  const [createDate, setCreateDate] = useState("");
  const [createTime, setCreateTime] = useState("");
  const [createAvailability, setCreateAvailability] =
    useState<AvailabilityState>("idle");

  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const [rescheduleDate, setRescheduleDate] = useState("");
  const [rescheduleTime, setRescheduleTime] = useState("");
  const [rescheduleAvailability, setRescheduleAvailability] =
    useState<AvailabilityState>("idle");

  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [publicBookingOpen, setPublicBookingOpen] = useState(false);
  const [publicBookingLoading, setPublicBookingLoading] = useState(false);
  const [publicBookingSaving, setPublicBookingSaving] = useState(false);
  const [publicBookingError, setPublicBookingError] = useState<string | null>(null);
  const [publicBookingState, setPublicBookingState] = useState<{ enabled: boolean; key?: string | null } | null>(null);

  const customerList = customers.data ?? [];
  const serviceList = services.data ?? [];
  const bookingList = bookings.data ?? [];

  const selectedBooking = bookingList.find(item => item.id === selectedId) ?? null;
  const selectedCustomer = customerFor(selectedBooking, customerList);
  const selectedService = serviceFor(selectedBooking, serviceList);

  const conversation = useQuery({
    queryKey: ["agenda", "booking-context", selectedId],
    queryFn: () => getBookingContext(selectedId as string),
    enabled: Boolean(selectedId && detailTab === "conversation"),
    ...queryDefaults
  });

  const activity = useQuery({
    queryKey: ["agenda", "booking-activity", selectedId],
    queryFn: () => getBookingActivity(selectedId as string),
    enabled: Boolean(selectedId && detailTab === "activity"),
    ...queryDefaults
  });

  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("es");
    return bookingList.filter(booking => {
      const customer = customerFor(booking, customerList);
      const service = serviceFor(booking, serviceList);
      if (serviceFilter !== "all" && booking.serviceId !== serviceFilter) return false;
      if (statusFilter !== "all" && booking.status !== statusFilter) return false;
      if (!query) return true;
      return [
        customer?.name,
        customer?.phone,
        service?.name,
        sourceLabel(booking.source),
        statusLabel(booking.status)
      ].filter(Boolean).join(" ").toLocaleLowerCase("es").includes(query);
    });
  }, [bookingList, customerList, search, serviceFilter, serviceList, statusFilter]);

  const roles = me.data?.roles ?? [];
  const permissions = me.data?.permissions ?? [];
  const canManage = permissions.length
    ? permissions.some(permission =>
        ["BOOKINGS_MANAGE", "PERM_BOOKINGS_MANAGE"].includes(permission)
      )
    : roles.some(role => ["BUSINESS_ADMIN", "BUSINESS_OWNER", "OPERATOR"].includes(role));

  const canManageCustomers = permissions.length
    ? permissions.some(permission =>
        ["CUSTOMERS_MANAGE", "PERM_CUSTOMERS_MANAGE"].includes(permission)
      )
    : roles.some(role => ["BUSINESS_ADMIN", "BUSINESS_OWNER", "OPERATOR"].includes(role));

  const canPublishPublicBooking = roles.some(role =>
    ["BUSINESS_ADMIN", "BUSINESS_OWNER"].includes(role)
  );

  async function openPublicBookingSettings() {
    setPublicBookingOpen(true);
    setPublicBookingLoading(true);
    setPublicBookingError(null);
    try {
      setPublicBookingState(await getPublicBookingState());
    } catch (error) {
      setPublicBookingError(
        error instanceof Error ? error.message : "No pudimos cargar las reservas online."
      );
    } finally {
      setPublicBookingLoading(false);
    }
  }

  async function togglePublicBooking(enabled: boolean) {
    if (publicBookingSaving) return;
    setPublicBookingSaving(true);
    setPublicBookingError(null);
    try {
      setPublicBookingState(await updatePublicBookingState(enabled));
    } catch (error) {
      setPublicBookingError(
        error instanceof Error ? error.message : "No pudimos actualizar las reservas online."
      );
    } finally {
      setPublicBookingSaving(false);
    }
  }

  function openBooking(booking: Booking) {
    setSelectedId(booking.id);
    setDetailTab("summary");
    setRescheduleOpen(false);
    setActionError(null);
  }

  function closeBooking() {
    setSelectedId(null);
    setDetailTab("summary");
    setRescheduleOpen(false);
    setActionError(null);
  }

  async function verifyCreateAvailability() {
    setActionError(null);
    setCreateAvailability("checking");
    try {
      const result = await checkAvailability(
        createServiceId,
        toIso(createDate, createTime)
      );
      setCreateAvailability(result.available ? "available" : "unavailable");
    } catch (error) {
      setCreateAvailability("error");
      setActionError(error instanceof Error ? error.message : "No pudimos comprobar disponibilidad.");
    }
  }

  async function submitCreate() {
    if (saving || createAvailability !== "available") return;
    setSaving(true);
    setActionError(null);
    try {
      await createBooking({
        customerId: createCustomerId,
        serviceId: createServiceId,
        startAt: toIso(createDate, createTime),
        source: "ADMIN",
        notes: null
      });
      await bookings.refetch();
      setCreateOpen(false);
      setCreateAvailability("idle");
      setCreateCustomerId("");
      setCreateServiceId("");
      setCreateDate("");
      setCreateTime("");
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No pudimos crear la reserva.");
    } finally {
      setSaving(false);
    }
  }

  function beginReschedule() {
    if (!selectedBooking) return;
    setRescheduleDate(datePart(selectedBooking.startAt));
    setRescheduleTime(timePart(selectedBooking.startAt));
    setRescheduleAvailability("idle");
    setActionError(null);
    setRescheduleOpen(true);
  }

  async function verifyRescheduleAvailability() {
    if (!selectedBooking) return;
    setActionError(null);
    setRescheduleAvailability("checking");
    try {
      const result = await checkAvailability(
        selectedBooking.serviceId,
        toIso(rescheduleDate, rescheduleTime),
        selectedBooking.id
      );
      setRescheduleAvailability(result.available ? "available" : "unavailable");
    } catch (error) {
      setRescheduleAvailability("error");
      setActionError(error instanceof Error ? error.message : "No pudimos comprobar disponibilidad.");
    }
  }

  async function submitReschedule() {
    if (!selectedBooking || saving || rescheduleAvailability !== "available") return;
    setSaving(true);
    setActionError(null);
    try {
      await rescheduleBooking(selectedBooking.id, {
        startAt: toIso(rescheduleDate, rescheduleTime),
        notes: selectedBooking.notes ?? null
      });
      await bookings.refetch();
      setRescheduleOpen(false);
      setRescheduleAvailability("idle");
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No pudimos reprogramar la reserva.");
    } finally {
      setSaving(false);
    }
  }

  async function submitCancel() {
    if (!selectedBooking || saving) return;
    if (!window.confirm("¿Cancelar esta reserva?")) return;
    setSaving(true);
    setActionError(null);
    try {
      await cancelBooking(selectedBooking.id);
      await bookings.refetch();
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No pudimos cancelar la reserva.");
    } finally {
      setSaving(false);
    }
  }

  const primaryLoading = bookings.isPending || customers.isPending || services.isPending;

  return (
    <AppShell>
      <main className={styles.page} data-visual-page="agenda">
        <header className={styles.pageHeader}>
          <div>
            <p className={styles.eyebrow}>OPERACIÓN</p>
            <h1>Agenda</h1>
            <p>Gestiona tus citas y reservas</p>
          </div>
          <div className={styles.formActions}>
            {canPublishPublicBooking && (
              <button
                className={styles.secondaryButton}
                type="button"
                onClick={() => void openPublicBookingSettings()}
              >
                Reservas online
              </button>
            )}
            {canManage && (
              <button className={styles.primaryButton} type="button" onClick={() => {
                setCreateOpen(true);
                setActionError(null);
                setCreateAvailability("idle");
              }}>
                + Nueva cita
              </button>
            )}
          </div>
        </header>

        <section className={styles.visualHero} aria-label="Agenda asistida por RecepVoz">
          <div className={styles.visualHeroCopy}>
            <span className={styles.visualHeroKicker}>AGENDA EN VIVO</span>
            <h2>RecepVoz organiza tus citas <span>mientras tú trabajas</span></h2>
            <p>
              Reservas, clientes y conversaciones permanecen conectados en una sola vista operativa.
            </p>
            <div className={styles.visualHeroSignals}>
              <span><i data-tone="cyan" />{bookingList.length} reservas visibles</span>
              <span><i data-tone="green" />{bookingList.filter(item => item.status === "CONFIRMED").length} confirmadas</span>
              <span><i data-tone="violet" />{bookingList.filter(item => item.status === "PENDING").length} por revisar</span>
            </div>
          </div>
          <div className={styles.visualHeroArt} aria-hidden="true">
            <span className={styles.visualHeroOrbit} />
            <img className={styles.visualHeroRobot} src="/app/assets/recepvoz/v2/agenda/hero-calendar-robot.webp" alt="" />
            <img className={styles.visualHeroCalendar} src="/app/assets/recepvoz/v2/agenda/appointments-calendar.webp" alt="" />
          </div>
        </section>

        <section className={styles.metrics} aria-label="Resumen de Agenda">
          <article>
            <span>Reservas visibles</span>
            <strong>{bookingList.length}</strong>
          </article>
          <article>
            <span>Confirmadas</span>
            <strong>{bookingList.filter(item => item.status === "CONFIRMED").length}</strong>
          </article>
          <article>
            <span>Requieren atención</span>
            <strong>{bookingList.filter(item => item.status === "PENDING").length}</strong>
          </article>
        </section>

        <section className={styles.filters} aria-label="Filtros de Agenda">
          <label className={styles.search}>
            <span className={styles.srOnly}>Buscar reservas</span>
            <input
              type="search"
              aria-label="Buscar reservas"
              placeholder="Buscar por cliente, teléfono o servicio..."
              value={search}
              onChange={event => setSearch(event.currentTarget.value)}
            />
          </label>
          <label>
            <span>Servicio</span>
            <select
              aria-label="Servicio"
              value={serviceFilter}
              onChange={event => setServiceFilter(event.currentTarget.value)}
            >
              <option value="all">Todos</option>
              {serviceList.map(service => (
                <option key={service.id} value={service.id}>{service.name}</option>
              ))}
            </select>
          </label>
          <label>
            <span>Estado</span>
            <select
              aria-label="Estado"
              value={statusFilter}
              onChange={event => setStatusFilter(event.currentTarget.value)}
            >
              <option value="all">Todos</option>
              <option value="CONFIRMED">Confirmadas</option>
              <option value="PENDING">Pendientes</option>
              <option value="CANCELLED">Canceladas</option>
            </select>
          </label>
          <button
            className={styles.secondaryButton}
            type="button"
            onClick={() => {
              setSearch("");
              setServiceFilter("all");
              setStatusFilter("all");
            }}
          >
            Limpiar
          </button>
        </section>

        <div className={styles.workspace + (selectedBooking ? " " + styles.hasDetail : "")}>
          <section className={styles.listPane} aria-label="Reservas">
            {primaryLoading ? (
              <div className={styles.state} role="status">Cargando reservas…</div>
            ) : bookings.isError ? (
              <div className={styles.state} role="alert">
                <strong>No pudimos cargar las reservas.</strong>
                <button type="button" onClick={() => bookings.refetch()}>Reintentar</button>
              </div>
            ) : filtered.length === 0 ? (
              <div className={styles.state}>
                <strong>No hay reservas que coincidan.</strong>
                <span>Prueba con otros filtros o una búsqueda distinta.</span>
              </div>
            ) : (
              <div className={styles.tableWrap}>
                <table aria-label="Reservas">
                  <thead>
                    <tr>
                      <th scope="col" role="columnheader">Fecha y hora</th>
                      <th scope="col" role="columnheader">Cliente</th>
                      <th scope="col" role="columnheader">Servicio</th>
                      <th scope="col" role="columnheader">Origen</th>
                      <th scope="col" role="columnheader">Estado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map(booking => {
                      const customer = customerFor(booking, customerList);
                      const service = serviceFor(booking, serviceList);
                      return (
                        <tr
                          key={booking.id}
                          data-testid={"agenda-row-" + booking.id}
                          className={selectedId === booking.id ? styles.selectedRow : undefined}
                          onClick={() => openBooking(booking)}
                          tabIndex={0}
                          onKeyDown={event => {
                            if (event.key === "Enter" || event.key === " ") openBooking(booking);
                          }}
                        >
                          <td><time dateTime={booking.startAt}>{formatDateTime(booking.startAt)}</time></td>
                          <td>
                            <strong>{customer?.name ?? "Cliente"}</strong>
                            <small>{customer?.phone ?? "Sin teléfono"}</small>
                          </td>
                          <td>{service?.name ?? "Servicio"}</td>
                          <td><span className={styles.sourceBadge}>{sourceLabel(booking.source)}</span></td>
                          <td>
                            <span className={styles.statusBadge + " " + (styles["status_" + booking.status] ?? "")}>
                              {statusLabel(booking.status)}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {selectedBooking && (
            <aside
              className={styles.detailPane}
              role="dialog"
              aria-modal="false"
              aria-label={"Reserva · " + (selectedCustomer?.name ?? "Cliente")}
            >
              <header className={styles.detailHeader}>
                <button className={styles.mobileBack} type="button" onClick={closeBooking}>
                  ← Volver a Agenda
                </button>
                <div>
                  <span className={styles.eyebrow}>RESERVA</span>
                  <h2>{selectedCustomer?.name ?? "Cliente"}</h2>
                  <p>{selectedService?.name ?? "Servicio"} · {formatDateTime(selectedBooking.startAt)}</p>
                </div>
                <button className={styles.closeButton} type="button" onClick={closeBooking} aria-label="Cerrar detalle">
                  ×
                </button>
              </header>

              <div className={styles.tabs} role="tablist" aria-label="Detalle de reserva">
                {([
                  ["summary", "Resumen"],
                  ["conversation", "Conversación"],
                  ["customer", "Cliente"],
                  ["activity", "Actividad"]
                ] as const).map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    role="tab"
                    aria-selected={detailTab === value}
                    onClick={() => setDetailTab(value)}
                  >
                    {label}
                  </button>
                ))}
              </div>

              <div className={styles.detailBody}>
                {detailTab === "summary" && (
                  <section className={styles.summaryGrid}>
                    <article>
                      <span>Estado</span>
                      <strong>{statusLabel(selectedBooking.status)}</strong>
                    </article>
                    <article>
                      <span>Origen</span>
                      <strong>{sourceLabel(selectedBooking.source)}</strong>
                    </article>
                    <article>
                      <span>Fecha y hora</span>
                      <strong>{formatDateTime(selectedBooking.startAt)}</strong>
                    </article>
                    <article>
                      <span>Servicio</span>
                      <strong>{selectedService?.name ?? "Sin servicio"}</strong>
                    </article>
                    <article className={styles.notes}>
                      <span>Notas</span>
                      <strong>{selectedBooking.notes || "Sin notas"}</strong>
                    </article>
                  </section>
                )}

                {detailTab === "conversation" && (
                  conversation.isPending ? (
                    <div className={styles.state} role="status">Cargando conversación…</div>
                  ) : conversation.isError ? (
                    <div className={styles.state} role="alert">
                      <strong>No pudimos cargar la conversación.</strong>
                      <span>La reserva sigue disponible y puedes continuar operando.</span>
                    </div>
                  ) : (
                    <ConversationPanel
                      customerLabel={selectedCustomer?.name}
                      context={conversation.data ?? null}
                    />
                  )
                )}

                {detailTab === "customer" && (
                  <section className={styles.customerCard}>
                    <span>Cliente relacionado</span>
                    <h3>{selectedCustomer?.name ?? "Cliente"}</h3>
                    <p>{selectedCustomer?.phone || "Sin teléfono"}</p>
                    <p>{selectedCustomer?.email || "Sin correo"}</p>
                    {selectedCustomer?.notes && <p>{selectedCustomer.notes}</p>}
                  </section>
                )}

                {detailTab === "activity" && (
                  activity.isPending ? (
                    <div className={styles.state} role="status">Cargando actividad…</div>
                  ) : activity.isError ? (
                    <div className={styles.state} role="alert">No pudimos cargar la actividad.</div>
                  ) : (
                    <div className={styles.activityList}>
                      {(activity.data ?? []).length === 0 ? (
                        <p>Sin actividad registrada.</p>
                      ) : (activity.data ?? []).map(item => (
                        <article key={item.id}>
                          <div>
                            <strong>{activityLabel(item.action)}</strong>
                            <span>{item.actorName || "Sistema"}</span>
                          </div>
                          <time>{formatDateTime(item.createdAt)}</time>
                        </article>
                      ))}
                    </div>
                  )
                )}

                {actionError && <div className={styles.error} role="alert">{actionError}</div>}

                {rescheduleOpen && selectedBooking && (
                  <section className={styles.inlineForm} aria-label="Reprogramar reserva">
                    <h3>Reprogramar</h3>
                    <div className={styles.formGrid}>
                      <label>
                        <span>Fecha</span>
                        <input
                          aria-label="Fecha"
                          type="date"
                          value={rescheduleDate}
                          onChange={event => {
                            setRescheduleDate(event.currentTarget.value);
                            setRescheduleAvailability("idle");
                          }}
                        />
                      </label>
                      <label>
                        <span>Hora</span>
                        <input
                          aria-label="Hora"
                          type="time"
                          value={rescheduleTime}
                          onChange={event => {
                            setRescheduleTime(event.currentTarget.value);
                            setRescheduleAvailability("idle");
                          }}
                        />
                      </label>
                    </div>
                    <div className={styles.formActions}>
                      <button
                        className={styles.secondaryButton}
                        type="button"
                        disabled={!rescheduleDate || !rescheduleTime || rescheduleAvailability === "checking"}
                        onClick={verifyRescheduleAvailability}
                      >
                        Comprobar disponibilidad
                      </button>
                      {rescheduleAvailability === "available" && <span className={styles.available}>Horario disponible</span>}
                      {rescheduleAvailability === "unavailable" && <span className={styles.unavailable}>Horario no disponible</span>}
                      <button
                        className={styles.primaryButton}
                        type="button"
                        disabled={saving || rescheduleAvailability !== "available"}
                        onClick={submitReschedule}
                      >
                        Confirmar cambio
                      </button>
                    </div>
                  </section>
                )}
              </div>

              {canManage && (
                <footer className={styles.detailActions}>
                  <button className={styles.primaryButton} type="button" onClick={beginReschedule}>
                    Reprogramar
                  </button>
                  <button
                    className={styles.dangerButton}
                    type="button"
                    disabled={saving || selectedBooking.status === "CANCELLED"}
                    onClick={submitCancel}
                  >
                    Cancelar reserva
                  </button>
                </footer>
              )}
            </aside>
          )}
        </div>

        {publicBookingOpen && canPublishPublicBooking && (
          <div className={styles.modalBackdrop}>
            <section className={styles.modal} role="dialog" aria-modal="true" aria-label="Reservas online">
              <header className={styles.modalHeader}>
                <div>
                  <span className={styles.eyebrow}>AGENDA PÚBLICA</span>
                  <h2>Reservas online</h2>
                </div>
                <button
                  type="button"
                  className={styles.closeButton}
                  aria-label="Cerrar reservas online"
                  onClick={() => setPublicBookingOpen(false)}
                >
                  ×
                </button>
              </header>

              <div className={styles.formStack}>
                {publicBookingLoading ? (
                  <div className={styles.state} role="status">Cargando reservas online…</div>
                ) : publicBookingError ? (
                  <div className={styles.error} role="alert">{publicBookingError}</div>
                ) : publicBookingState ? (
                  <>
                    <div className={styles.state}>
                      <strong>{publicBookingState.enabled ? "Activadas" : "Desactivadas"}</strong>
                      <span>
                        {publicBookingState.enabled
                          ? "Tus clientes pueden reservar desde el enlace público."
                          : "Actívalas cuando quieras publicar un enlace para tus clientes."}
                      </span>
                    </div>

                    {publicBookingState.key && (
                      <label>
                        <span>Enlace público</span>
                        <input
                          aria-label="Enlace público"
                          readOnly
                          value={window.location.origin + "/reservar/?key=" + publicBookingState.key}
                        />
                      </label>
                    )}

                    <div className={styles.formActions}>
                      <button
                        className={publicBookingState.enabled ? styles.secondaryButton : styles.primaryButton}
                        type="button"
                        disabled={publicBookingSaving}
                        onClick={() => void togglePublicBooking(!publicBookingState.enabled)}
                      >
                        {publicBookingSaving
                          ? "Guardando…"
                          : publicBookingState.enabled
                            ? "Desactivar reservas online"
                            : "Activar reservas online"}
                      </button>
                    </div>
                  </>
                ) : null}
              </div>
            </section>
          </div>
        )}

        {createOpen && (
          <div className={styles.modalBackdrop}>
            <section className={styles.modal} role="dialog" aria-modal="true" aria-label="Nueva cita">
              <header className={styles.modalHeader}>
                <div>
                  <span className={styles.eyebrow}>AGENDA</span>
                  <h2>Nueva cita</h2>
                </div>
                <button
                  type="button"
                  className={styles.closeButton}
                  aria-label="Cerrar nueva cita"
                  onClick={() => setCreateOpen(false)}
                >
                  ×
                </button>
              </header>

              <div className={styles.formStack}>
                <label>
                  <span>Cliente</span>
                  <select
                    aria-label="Cliente"
                    value={createCustomerId}
                    onChange={event => {
                      setCreateCustomerId(event.currentTarget.value);
                      setCreateAvailability("idle");
                    }}
                  >
                    <option value="">Selecciona un cliente</option>
                    {customerList.map(customer => (
                      <option key={customer.id} value={customer.id}>{customer.name}</option>
                    ))}
                  </select>
                </label>
                {canManageCustomers && (
                  <CustomerQuickCreate
                    onCreated={async customer => {
                      await customers.refetch();
                      setCreateCustomerId(customer.id);
                      setCreateAvailability("idle");
                    }}
                  />
                )}
                <label>
                  <span>Servicio</span>
                  <select
                    aria-label="Servicio"
                    value={createServiceId}
                    onChange={event => {
                      setCreateServiceId(event.currentTarget.value);
                      setCreateAvailability("idle");
                    }}
                  >
                    <option value="">Selecciona un servicio</option>
                    {serviceList.map(service => (
                      <option key={service.id} value={service.id}>{service.name}</option>
                    ))}
                  </select>
                </label>
                <div className={styles.formGrid}>
                  <label>
                    <span>Fecha</span>
                    <input
                      aria-label="Fecha"
                      type="date"
                      value={createDate}
                      onChange={event => {
                        setCreateDate(event.currentTarget.value);
                        setCreateAvailability("idle");
                      }}
                    />
                  </label>
                  <label>
                    <span>Hora</span>
                    <input
                      aria-label="Hora"
                      type="time"
                      value={createTime}
                      onChange={event => {
                        setCreateTime(event.currentTarget.value);
                        setCreateAvailability("idle");
                      }}
                    />
                  </label>
                </div>

                {actionError && <div className={styles.error} role="alert">{actionError}</div>}
                {createAvailability === "available" && <div className={styles.available}>Horario disponible</div>}
                {createAvailability === "unavailable" && <div className={styles.unavailable}>Horario no disponible</div>}

                <div className={styles.formActions}>
                  <button
                    type="button"
                    className={styles.secondaryButton}
                    disabled={
                      !createCustomerId ||
                      !createServiceId ||
                      !createDate ||
                      !createTime ||
                      createAvailability === "checking"
                    }
                    onClick={verifyCreateAvailability}
                  >
                    Comprobar disponibilidad
                  </button>
                  <button
                    type="button"
                    className={styles.primaryButton}
                    disabled={saving || createAvailability !== "available"}
                    onClick={submitCreate}
                  >
                    Crear reserva
                  </button>
                </div>
              </div>
            </section>
          </div>
        )}
      </main>
    </AppShell>
  );
}
