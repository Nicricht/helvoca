import React, { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { CalendarDays, CheckCircle2, Clock3, MapPin, ShieldCheck } from "lucide-react";
import "./public-booking.css";

interface PublicService {
  id: string;
  name: string;
  description?: string | null;
  durationMinutes: number;
  price?: number | null;
  currency?: string | null;
}

interface BookingPage {
  name: string;
  timezone: string;
  description?: string | null;
  address?: string | null;
  services: PublicService[];
}

interface AvailabilitySlot {
  startAt: string;
  endAt: string;
}

interface AvailabilityResponse {
  date: string;
  timezone: string;
  slots: AvailabilitySlot[];
}

interface Confirmation {
  id: string;
  status: string;
  serviceName: string;
  startAt: string;
  timezone: string;
  customerName?: string | null;
}

class PublicApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

async function publicRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...(init?.headers ?? {})
    }
  });

  if (!response.ok) {
    let message = "No pudimos completar la solicitud.";
    try {
      const body = await response.json();
      if (typeof body?.message === "string" && body.message.trim()) message = body.message;
    } catch {
      // Keep the human fallback when the server does not return JSON.
    }
    throw new PublicApiError(response.status, message);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

function formatMoney(value: number | null | undefined, currency?: string | null) {
  if (value === null || value === undefined) return null;
  try {
    return new Intl.NumberFormat("es-CL", {
      style: "currency",
      currency: currency || "CLP",
      maximumFractionDigits: currency === "CLP" || !currency ? 0 : 2
    }).format(value);
  } catch {
    return String(value);
  }
}

function slotTime(slot: AvailabilitySlot, timezone: string) {
  const date = new Date(slot.startAt);
  if (Number.isNaN(date.getTime())) return slot.startAt;
  return new Intl.DateTimeFormat("es-CL", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: timezone
  }).format(date);
}

function longDate(value: string, timezone: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("es-CL", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: timezone
  }).format(date);
}

function newIdempotencyKey() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `public-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function PublicBookingApp() {
  const key = useMemo(
    () => new URLSearchParams(window.location.search).get("key")?.trim() ?? "",
    []
  );
  const [pageData, setPageData] = useState<BookingPage | null>(null);
  const [pageState, setPageState] = useState<"loading" | "ready" | "invalid" | "error">("loading");
  const [selectedService, setSelectedService] = useState<PublicService | null>(null);
  const [date, setDate] = useState("");
  const [availabilityState, setAvailabilityState] =
    useState<"idle" | "loading" | "ready" | "error">("idle");
  const [slots, setSlots] = useState<AvailabilitySlot[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<AvailabilitySlot | null>(null);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const submitLock = useRef(false);
  const idempotencyKey = useRef<string | null>(null);

  useEffect(() => {
    if (!key) {
      setPageState("invalid");
      return;
    }

    const controller = new AbortController();
    setPageState("loading");
    publicRequest<BookingPage>(
      `/api/v1/public/booking-pages/${encodeURIComponent(key)}`,
      { signal: controller.signal }
    )
      .then(data => {
        setPageData(data);
        setPageState("ready");
      })
      .catch(reason => {
        if (controller.signal.aborted) return;
        if (reason instanceof PublicApiError && reason.status === 404) {
          setPageState("invalid");
        } else {
          setPageState("error");
        }
      });

    return () => controller.abort();
  }, [key]);

  const loadAvailability = useCallback(async () => {
    if (!key || !pageData || !selectedService || !date) {
      setAvailabilityState("idle");
      setSlots([]);
      return;
    }

    setAvailabilityState("loading");
    setError("");
    setSelectedSlot(null);
    idempotencyKey.current = null;
    try {
      const params = new URLSearchParams({
        serviceId: selectedService.id,
        date
      });
      const data = await publicRequest<AvailabilityResponse>(
        `/api/v1/public/booking-pages/${encodeURIComponent(key)}/availability?${params.toString()}`
      );
      setSlots(data.slots ?? []);
      setAvailabilityState("ready");
    } catch (reason) {
      setSlots([]);
      setAvailabilityState("error");
      setError(reason instanceof Error ? reason.message : "No pudimos cargar los horarios.");
    }
  }, [date, key, pageData, selectedService]);

  useEffect(() => {
    void loadAvailability();
  }, [loadAvailability]);

  function chooseService(service: PublicService) {
    setSelectedService(service);
    setDate("");
    setSlots([]);
    setSelectedSlot(null);
    setConfirmation(null);
    setError("");
    setAvailabilityState("idle");
    idempotencyKey.current = null;
  }

  function chooseSlot(slot: AvailabilitySlot) {
    setSelectedSlot(slot);
    setError("");
    idempotencyKey.current = newIdempotencyKey();
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedService || !selectedSlot || submitLock.current) return;

    const form = new FormData(event.currentTarget);
    const name = String(form.get("name") ?? "").trim();
    const phone = String(form.get("phone") ?? "").trim();
    const email = String(form.get("email") ?? "").trim();

    if (!name || !phone) {
      setError("Escribe tu nombre y teléfono para confirmar la reserva.");
      return;
    }

    submitLock.current = true;
    setSubmitting(true);
    setError("");
    const requestKey = idempotencyKey.current ?? newIdempotencyKey();
    idempotencyKey.current = requestKey;

    try {
      const result = await publicRequest<Confirmation>(
        `/api/v1/public/booking-pages/${encodeURIComponent(key)}/bookings`,
        {
          method: "POST",
          headers: { "Idempotency-Key": requestKey },
          body: JSON.stringify({
            serviceId: selectedService.id,
            startAt: selectedSlot.startAt,
            customer: {
              name,
              phone,
              email: email || null
            }
          })
        }
      );
      setConfirmation(result);
    } catch (reason) {
      if (reason instanceof PublicApiError && reason.status === 409) {
        setError(reason.message || "El horario ya no está disponible. Elige otro.");
        setSelectedSlot(null);
        idempotencyKey.current = null;
        await loadAvailability();
      } else {
        setError(reason instanceof Error ? reason.message : "No pudimos crear la reserva.");
      }
    } finally {
      submitLock.current = false;
      setSubmitting(false);
    }
  }

  if (pageState === "loading") {
    return (
      <main className="public-booking-shell">
        <section className="public-card public-state" role="status">
          <span className="public-spinner" aria-hidden="true" />
          <h1>Preparando tu reserva</h1>
          <p>Estamos cargando los servicios y horarios disponibles.</p>
        </section>
      </main>
    );
  }

  if (pageState === "invalid") {
    return (
      <main className="public-booking-shell">
        <section className="public-card public-state">
          <span className="public-brand-mark" aria-hidden="true">H</span>
          <h1>Enlace no disponible</h1>
          <p>Este enlace de reservas no existe, está desactivado o ya no está disponible.</p>
        </section>
      </main>
    );
  }

  if (pageState === "error" || !pageData) {
    return (
      <main className="public-booking-shell">
        <section className="public-card public-state" role="alert">
          <h1>No pudimos cargar las reservas</h1>
          <p>Intenta nuevamente en unos minutos.</p>
          <button className="public-secondary-button" type="button" onClick={() => window.location.reload()}>
            Reintentar
          </button>
        </section>
      </main>
    );
  }

  if (confirmation) {
    return (
      <main className="public-booking-shell">
        <section className="public-card public-confirmation">
          <span className="public-success-icon" aria-hidden="true"><CheckCircle2 size={30} /></span>
          <p className="public-eyebrow">LISTO</p>
          <h1>Reserva confirmada</h1>
          <p className="public-lead">Tu hora quedó registrada en {pageData.name}.</p>
          <div className="public-confirmation-facts">
            <div>
              <span>Cliente</span>
              <strong>{confirmation.customerName || "Reserva confirmada"}</strong>
            </div>
            <div>
              <span>Servicio</span>
              <strong>{confirmation.serviceName}</strong>
            </div>
            <div>
              <span>Fecha y hora</span>
              <strong>{longDate(confirmation.startAt, confirmation.timezone || pageData.timezone)}</strong>
              <small>{slotTime({ startAt: confirmation.startAt, endAt: confirmation.startAt }, confirmation.timezone || pageData.timezone)}</small>
            </div>
          </div>
          <p className="public-quiet">Puedes cerrar esta página. El negocio ya tiene tu reserva.</p>
        </section>
      </main>
    );
  }

  return (
    <main className="public-booking-shell">
      <section className="public-card public-booking-card">
        <header className="public-header">
          <div className="public-brand-row">
            <span className="public-brand-mark" aria-hidden="true">H</span>
            <span>HELVOCA</span>
          </div>
          <p className="public-eyebrow">RESERVAS EN LÍNEA</p>
          <h1>{pageData.name}</h1>
          <p className="public-lead">{pageData.description || "Elige un servicio y encuentra una hora disponible."}</p>
          {pageData.address && (
            <p className="public-address"><MapPin size={15} aria-hidden="true" />{pageData.address}</p>
          )}
        </header>

        {error && <div className="public-alert" role="alert">{error}</div>}

        <section className="public-section" aria-labelledby="service-heading">
          <div className="public-section-heading">
            <span className="public-step">1</span>
            <div>
              <h2 id="service-heading">Elige un servicio</h2>
              <p>Solo mostramos servicios disponibles para reserva.</p>
            </div>
          </div>

          {pageData.services.length === 0 ? (
            <div className="public-empty">
              <p>Este negocio todavía no tiene servicios disponibles para reservar en línea.</p>
            </div>
          ) : (
            <div className="public-service-grid">
              {pageData.services.map(service => {
                const price = formatMoney(service.price, service.currency);
                return (
                  <button
                    key={service.id}
                    className={`public-service-card ${selectedService?.id === service.id ? "selected" : ""}`}
                    type="button"
                    aria-pressed={selectedService?.id === service.id}
                    onClick={() => chooseService(service)}
                  >
                    <span>
                      <strong>{service.name}</strong>
                      {service.description && <small>{service.description}</small>}
                    </span>
                    <span className="public-service-meta">
                      <span><Clock3 size={14} aria-hidden="true" />{service.durationMinutes} min</span>
                      {price && <strong>{price}</strong>}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </section>

        {selectedService && (
          <section className="public-section" aria-labelledby="time-heading">
            <div className="public-section-heading">
              <span className="public-step">2</span>
              <div>
                <h2 id="time-heading">Fecha y hora</h2>
                <p>Los horarios se consultan directamente con la agenda del negocio.</p>
              </div>
            </div>

            <label className="public-field public-date-field">
              <span>Fecha</span>
              <input
                type="date"
                value={date}
                onChange={event => setDate(event.currentTarget.value)}
              />
            </label>

            {availabilityState === "loading" && (
              <div className="public-inline-state" role="status">
                <span className="public-spinner small" aria-hidden="true" />
                Buscando horarios…
              </div>
            )}

            {availabilityState === "error" && (
              <button className="public-secondary-button" type="button" onClick={() => void loadAvailability()}>
                Reintentar horarios
              </button>
            )}

            {availabilityState === "ready" && date && slots.length === 0 && (
              <div className="public-empty">
                <CalendarDays size={21} aria-hidden="true" />
                <p>No hay horas disponibles ese día. Prueba otra fecha.</p>
              </div>
            )}

            {slots.length > 0 && (
              <div className="public-slots" aria-label="Horarios disponibles">
                {slots.map(slot => {
                  const label = slotTime(slot, pageData.timezone);
                  return (
                    <button
                      key={slot.startAt}
                      type="button"
                      className={selectedSlot?.startAt === slot.startAt ? "selected" : ""}
                      onClick={() => chooseSlot(slot)}
                    >
                      {label}
                    </button>
                  );
                })}
              </div>
            )}
          </section>
        )}

        {selectedService && selectedSlot && (
          <section className="public-section" aria-labelledby="details-heading">
            <div className="public-section-heading">
              <span className="public-step">3</span>
              <div>
                <h2 id="details-heading">Tus datos</h2>
                <p>Los usaremos únicamente para identificar esta reserva.</p>
              </div>
            </div>

            <form className="public-form" onSubmit={submit}>
              <label className="public-field">
                <span>Nombre</span>
                <input name="name" autoComplete="name" maxLength={150} required />
              </label>
              <label className="public-field">
                <span>Teléfono</span>
                <input
                  name="phone"
                  type="tel"
                  autoComplete="tel"
                  placeholder="+56912345678"
                  maxLength={30}
                  required
                />
              </label>
              <label className="public-field">
                <span>Email</span>
                <input name="email" type="email" autoComplete="email" maxLength={180} />
              </label>

              <div className="public-selection-summary">
                <strong>{selectedService.name}</strong>
                <span>{date} · {slotTime(selectedSlot, pageData.timezone)}</span>
              </div>

              <button className="public-primary-button" type="submit" disabled={submitting}>
                {submitting ? "Confirmando…" : "Confirmar reserva"}
              </button>
            </form>
          </section>
        )}

        <footer className="public-footer">
          <ShieldCheck size={15} aria-hidden="true" />
          <span>Disponibilidad verificada antes de confirmar. Gestionado con Helvoca.</span>
        </footer>
      </section>
    </main>
  );
}

const root = document.getElementById("root");
if (!root) throw new Error("Public booking root not found");
createRoot(root).render(
  <React.StrictMode>
    <PublicBookingApp />
  </React.StrictMode>
);
