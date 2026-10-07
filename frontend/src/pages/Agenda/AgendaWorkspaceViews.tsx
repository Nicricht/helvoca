import type { Booking, Customer, ServiceItem } from "../../features/agenda/api";
import styles from "./AgendaPage.module.css";

export type AgendaViewMode = "day" | "week" | "month" | "list";

const VIEW_OPTIONS: Array<{ value: AgendaViewMode; label: string }> = [
  { value: "day", label: "Día" },
  { value: "week", label: "Semana" },
  { value: "month", label: "Mes" },
  { value: "list", label: "Lista" }
];

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

function statusLabel(value?: string | null) {
  return STATUS_LABELS[String(value ?? "").toUpperCase()] ?? value ?? "Sin estado";
}

function sourceLabel(value?: string | null) {
  return SOURCE_LABELS[String(value ?? "").toUpperCase()] ?? value ?? "Sin origen";
}

function dateKey(value: Date) {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function startOfDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate());
}

function startOfWeek(value: Date) {
  const date = startOfDay(value);
  const day = date.getDay();
  const distanceFromMonday = day === 0 ? 6 : day - 1;
  date.setDate(date.getDate() - distanceFromMonday);
  return date;
}

function addDays(value: Date, amount: number) {
  const next = new Date(value);
  next.setDate(next.getDate() + amount);
  return next;
}

function firstValidBookingDate(bookings: Booking[]) {
  const sorted = bookings
    .map(booking => new Date(booking.startAt))
    .filter(date => !Number.isNaN(date.getTime()))
    .sort((a, b) => a.getTime() - b.getTime());
  return sorted[0] ?? new Date();
}

function visibleDates(view: Exclude<AgendaViewMode, "list">, anchor: Date) {
  if (view === "day") return [startOfDay(anchor)];

  if (view === "week") {
    const start = startOfWeek(anchor);
    return Array.from({ length: 7 }, (_, index) => addDays(start, index));
  }

  const first = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
  const last = new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0);
  return Array.from({ length: last.getDate() }, (_, index) =>
    new Date(anchor.getFullYear(), anchor.getMonth(), index + 1)
  );
}

function dateHeading(value: Date, view: Exclude<AgendaViewMode, "list">) {
  if (view === "month") {
    return new Intl.DateTimeFormat("es-CL", {
      weekday: "short",
      day: "numeric"
    }).format(value);
  }

  return new Intl.DateTimeFormat("es-CL", {
    weekday: "short",
    day: "numeric",
    month: "short"
  }).format(value);
}

function rangeLabel(view: Exclude<AgendaViewMode, "list">, dates: Date[]) {
  if (dates.length === 0) return "";

  if (view === "day") {
    return new Intl.DateTimeFormat("es-CL", {
      weekday: "long",
      day: "numeric",
      month: "long",
      year: "numeric"
    }).format(dates[0]);
  }

  if (view === "month") {
    return new Intl.DateTimeFormat("es-CL", {
      month: "long",
      year: "numeric"
    }).format(dates[0]);
  }

  const compact = new Intl.DateTimeFormat("es-CL", {
    day: "numeric",
    month: "short"
  });
  return `${compact.format(dates[0])} – ${compact.format(dates[dates.length - 1])}`;
}

export function AgendaViewSwitcher({
  value,
  onChange
}: {
  value: AgendaViewMode;
  onChange: (value: AgendaViewMode) => void;
}) {
  return (
    <div className={styles.viewSwitcher} role="tablist" aria-label="Vista de Agenda">
      {VIEW_OPTIONS.map(option => (
        <button
          key={option.value}
          type="button"
          role="tab"
          aria-selected={value === option.value}
          className={value === option.value ? styles.activeView : undefined}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

export function AgendaScheduleFoundation({
  view,
  bookings,
  customers,
  services,
  selectedId,
  onSelect
}: {
  view: Exclude<AgendaViewMode, "list">;
  bookings: Booking[];
  customers: Customer[];
  services: ServiceItem[];
  selectedId: string | null;
  onSelect: (booking: Booking) => void;
}) {
  const anchor = firstValidBookingDate(bookings);
  const dates = visibleDates(view, anchor);
  const bookingsByDate = new Map<string, Booking[]>();

  bookings.forEach(booking => {
    const date = new Date(booking.startAt);
    if (Number.isNaN(date.getTime())) return;
    const key = dateKey(date);
    const dayBookings = bookingsByDate.get(key) ?? [];
    dayBookings.push(booking);
    dayBookings.sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime());
    bookingsByDate.set(key, dayBookings);
  });

  const customerMap = new Map(customers.map(customer => [customer.id, customer]));
  const serviceMap = new Map(services.map(service => [service.id, service]));

  return (
    <section className={styles.scheduleFoundation} aria-label={`Vista ${view === "day" ? "Día" : view === "week" ? "Semana" : "Mes"}`}>
      <header className={styles.scheduleFoundationHeader}>
        <div>
          <span>AGENDA OPERATIVA</span>
          <strong>{rangeLabel(view, dates)}</strong>
        </div>
        <p>La distribución horaria precisa y navegación temporal se completa en la Parte 2.</p>
      </header>

      <div
        className={styles.scheduleDateGrid}
        data-agenda-view={view}
        data-testid="agenda-calendar-foundation"
      >
        {dates.map((date, index) => {
          const dayBookings = bookingsByDate.get(dateKey(date)) ?? [];
          const isToday = dateKey(date) === dateKey(new Date());
          return (
            <article
              key={dateKey(date)}
              className={
                styles.scheduleDay +
                (isToday ? " " + styles.scheduleToday : "") +
                (view === "month" && index === 0 ? " " + styles.scheduleMonthFirst : "")
              }
              style={view === "month" && index === 0
                ? { gridColumnStart: ((date.getDay() + 6) % 7) + 1 }
                : undefined}
            >
              <header>
                <span>{dateHeading(date, view)}</span>
                {isToday && <small>Hoy</small>}
              </header>

              <div className={styles.scheduleDayBookings}>
                {dayBookings.length === 0 ? (
                  <span className={styles.scheduleEmpty}>Disponible</span>
                ) : dayBookings.map(booking => {
                  const customer = customerMap.get(booking.customerId);
                  const service = serviceMap.get(booking.serviceId);
                  const start = new Date(booking.startAt);
                  return (
                    <button
                      type="button"
                      key={booking.id}
                      data-testid={"agenda-row-" + booking.id}
                      className={
                        styles.scheduleBooking +
                        " " +
                        (styles["scheduleStatus_" + booking.status] ?? "") +
                        (selectedId === booking.id ? " " + styles.scheduleBookingSelected : "")
                      }
                      onClick={() => onSelect(booking)}
                    >
                      <span className={styles.scheduleBookingTime}>
                        {new Intl.DateTimeFormat("es-CL", {
                          hour: "2-digit",
                          minute: "2-digit",
                          hour12: false
                        }).format(start)}
                      </span>
                      <strong>{customer?.name ?? "Cliente"}</strong>
                      <span>{service?.name ?? "Servicio"}</span>
                      <small>{sourceLabel(booking.source)} · {statusLabel(booking.status)}</small>
                    </button>
                  );
                })}
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}
