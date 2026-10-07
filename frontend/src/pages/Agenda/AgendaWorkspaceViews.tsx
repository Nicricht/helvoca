import { useEffect, useMemo, useState } from "react";
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

const DEFAULT_START_HOUR = 7;
const DEFAULT_END_HOUR = 21;
const HOUR_HEIGHT = 72;
const MIN_BOOKING_HEIGHT = 34;

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

function addMonths(value: Date, amount: number) {
  return new Date(value.getFullYear(), value.getMonth() + amount, 1);
}

function visibleDates(view: Exclude<AgendaViewMode, "list">, anchor: Date) {
  if (view === "day") return [startOfDay(anchor)];

  if (view === "week") {
    const start = startOfWeek(anchor);
    return Array.from({ length: 7 }, (_, index) => addDays(start, index));
  }

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

function minutesSinceMidnight(value: Date) {
  return value.getHours() * 60 + value.getMinutes();
}

function bookingEnd(booking: Booking, services: Map<string, ServiceItem>) {
  const explicit = booking.endAt ? new Date(booking.endAt) : null;
  if (explicit && !Number.isNaN(explicit.getTime())) return explicit;

  const start = new Date(booking.startAt);
  const service = services.get(booking.serviceId);
  const duration = Math.max(15, service?.durationMinutes ?? 30);
  return new Date(start.getTime() + duration * 60_000);
}

function bookingDurationMinutes(booking: Booking, services: Map<string, ServiceItem>) {
  const start = new Date(booking.startAt);
  const end = bookingEnd(booking, services);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return 30;
  return Math.max(15, Math.round((end.getTime() - start.getTime()) / 60_000));
}

function scheduleBounds(bookings: Booking[], services: Map<string, ServiceItem>) {
  let startHour = DEFAULT_START_HOUR;
  let endHour = DEFAULT_END_HOUR;

  bookings.forEach(booking => {
    const start = new Date(booking.startAt);
    if (Number.isNaN(start.getTime())) return;
    const end = bookingEnd(booking, services);
    startHour = Math.min(startHour, Math.max(0, start.getHours()));
    endHour = Math.max(endHour, Math.min(24, Math.ceil(minutesSinceMidnight(end) / 60)));
  });

  if (endHour <= startHour) endHour = startHour + 1;
  return { startHour, endHour };
}

function navigationLabel(direction: "previous" | "next", view: Exclude<AgendaViewMode, "list">) {
  if (view === "day") return direction === "previous" ? "Día anterior" : "Día siguiente";
  if (view === "week") return direction === "previous" ? "Semana anterior" : "Semana siguiente";
  return direction === "previous" ? "Mes anterior" : "Mes siguiente";
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
  const [anchorDate, setAnchorDate] = useState(() => startOfDay(new Date()));
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  const dates = useMemo(() => visibleDates(view, anchorDate), [anchorDate, view]);
  const customerMap = useMemo(
    () => new Map(customers.map(customer => [customer.id, customer])),
    [customers]
  );
  const serviceMap = useMemo(
    () => new Map(services.map(service => [service.id, service])),
    [services]
  );

  const bookingsByDate = useMemo(() => {
    const grouped = new Map<string, Booking[]>();
    bookings.forEach(booking => {
      const date = new Date(booking.startAt);
      if (Number.isNaN(date.getTime())) return;
      const key = dateKey(date);
      const dayBookings = grouped.get(key) ?? [];
      dayBookings.push(booking);
      dayBookings.sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime());
      grouped.set(key, dayBookings);
    });
    return grouped;
  }, [bookings]);

  const visibleBookings = useMemo(() => {
    const visibleKeys = new Set(dates.map(dateKey));
    return bookings.filter(booking => {
      const start = new Date(booking.startAt);
      return !Number.isNaN(start.getTime()) && visibleKeys.has(dateKey(start));
    });
  }, [bookings, dates]);

  const { startHour, endHour } = useMemo(() => {
    const bounds = scheduleBounds(visibleBookings, serviceMap);
    const includesToday = dates.some(date => dateKey(date) === dateKey(now));
    if (!includesToday) return bounds;

    const currentHour = now.getHours();
    return {
      startHour: Math.min(bounds.startHour, currentHour),
      endHour: Math.max(bounds.endHour, Math.min(24, currentHour + 1))
    };
  }, [dates, now, serviceMap, visibleBookings]);

  const hours = useMemo(
    () => Array.from({ length: endHour - startHour + 1 }, (_, index) => startHour + index),
    [endHour, startHour]
  );

  const timelineHeight = (endHour - startHour) * HOUR_HEIGHT;

  function moveAnchor(direction: -1 | 1) {
    setAnchorDate(current => {
      if (view === "day") return addDays(current, direction);
      if (view === "week") return addDays(current, direction * 7);
      return addMonths(current, direction);
    });
  }

  function bookingStyle(booking: Booking) {
    const start = new Date(booking.startAt);
    const startMinutes = minutesSinceMidnight(start);
    const duration = bookingDurationMinutes(booking, serviceMap);
    const top = Math.max(0, ((startMinutes - startHour * 60) / 60) * HOUR_HEIGHT);
    const height = Math.max(MIN_BOOKING_HEIGHT, (duration / 60) * HOUR_HEIGHT);
    return { top: `${top}px`, height: `${height}px` };
  }

  function bookingCard(booking: Booking, compact = false) {
    const customer = customerMap.get(booking.customerId);
    const service = serviceMap.get(booking.serviceId);
    const start = new Date(booking.startAt);
    const duration = bookingDurationMinutes(booking, serviceMap);

    return (
      <button
        type="button"
        key={booking.id}
        data-testid={"agenda-row-" + booking.id}
        data-duration-minutes={duration}
        className={
          styles.scheduleBooking +
          " " +
          (styles["scheduleStatus_" + booking.status] ?? "") +
          (selectedId === booking.id ? " " + styles.scheduleBookingSelected : "") +
          (compact ? " " + styles.scheduleBookingCompact : "")
        }
        style={compact ? undefined : bookingStyle(booking)}
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
        {!compact && <small>{sourceLabel(booking.source)} · {statusLabel(booking.status)}</small>}
      </button>
    );
  }

  const nowMinutes = minutesSinceMidnight(now);
  const nowTop = ((nowMinutes - startHour * 60) / 60) * HOUR_HEIGHT;
  const showNow = nowTop >= 0 && nowTop <= timelineHeight;

  return (
    <section
      className={styles.scheduleFoundation}
      aria-label={`Vista ${view === "day" ? "Día" : view === "week" ? "Semana" : "Mes"}`}
    >
      <header className={styles.scheduleFoundationHeader}>
        <div>
          <span>AGENDA OPERATIVA</span>
          <strong>{rangeLabel(view, dates)}</strong>
        </div>

        <div className={styles.scheduleNavigation} aria-label="Navegación temporal">
          <button
            type="button"
            aria-label={navigationLabel("previous", view)}
            onClick={() => moveAnchor(-1)}
          >
            ‹
          </button>
          <button type="button" onClick={() => setAnchorDate(startOfDay(new Date()))}>
            Hoy
          </button>
          <button
            type="button"
            aria-label={navigationLabel("next", view)}
            onClick={() => moveAnchor(1)}
          >
            ›
          </button>
        </div>
      </header>

      {view === "month" ? (
        <div
          className={styles.scheduleMonthGrid}
          data-agenda-view={view}
          data-testid="agenda-calendar-foundation"
        >
          {dates.map((date, index) => {
            const dayBookings = bookingsByDate.get(dateKey(date)) ?? [];
            const isToday = dateKey(date) === dateKey(now);
            return (
              <article
                key={dateKey(date)}
                className={
                  styles.scheduleMonthDay +
                  (isToday ? " " + styles.scheduleToday : "")
                }
                style={index === 0
                  ? { gridColumnStart: ((date.getDay() + 6) % 7) + 1 }
                  : undefined}
              >
                <header>
                  <span>{dateHeading(date, view)}</span>
                  {isToday && <small>Hoy</small>}
                </header>
                <div className={styles.scheduleMonthBookings}>
                  {dayBookings.length === 0
                    ? <span className={styles.scheduleEmpty}>Disponible</span>
                    : dayBookings.slice(0, 3).map(booking => bookingCard(booking, true))}
                  {dayBookings.length > 3 && (
                    <small className={styles.scheduleMore}>+{dayBookings.length - 3} más</small>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      ) : (
        <div
          className={styles.scheduleTimelineShell}
          data-agenda-view={view}
          data-testid="agenda-calendar-foundation"
        >
          <div className={styles.scheduleTimeGutter} aria-hidden="true">
            <div className={styles.scheduleTimeHeaderSpacer} />
            <div className={styles.scheduleTimeLabels} style={{ height: `${timelineHeight}px` }}>
              {hours.slice(0, -1).map(hour => (
                <span
                  key={hour}
                  style={{ top: `${(hour - startHour) * HOUR_HEIGHT}px` }}
                >
                  {String(hour).padStart(2, "0")}:00
                </span>
              ))}
            </div>
          </div>

          <div
            className={styles.scheduleDayColumns}
            style={{
              gridTemplateColumns: view === "day"
                ? "minmax(0, 1fr)"
                : `repeat(${dates.length}, minmax(148px, 1fr))`
            }}
          >
            {dates.map(date => {
              const dayBookings = bookingsByDate.get(dateKey(date)) ?? [];
              const isToday = dateKey(date) === dateKey(now);
              return (
                <article
                  key={dateKey(date)}
                  className={
                    styles.scheduleTimelineDay +
                    (isToday ? " " + styles.scheduleToday : "")
                  }
                >
                  <header>
                    <span>{dateHeading(date, view)}</span>
                    {isToday && <small>Hoy</small>}
                  </header>

                  <div
                    className={styles.scheduleTimeline}
                    style={{ height: `${timelineHeight}px` }}
                  >
                    {hours.slice(0, -1).map(hour => (
                      <i
                        key={hour}
                        className={styles.scheduleHourRule}
                        aria-hidden="true"
                        style={{ top: `${(hour - startHour) * HOUR_HEIGHT}px` }}
                      />
                    ))}

                    {isToday && showNow && (
                      <div
                        className={styles.scheduleNowLine}
                        data-testid="agenda-now-line"
                        style={{ top: `${nowTop}px` }}
                        aria-label="Hora actual"
                      >
                        <span>Ahora</span>
                      </div>
                    )}

                    {dayBookings.map(booking => bookingCard(booking))}
                  </div>
                </article>
              );
            })}
          </div>
        </div>
      )}
    </section>
  );
}
