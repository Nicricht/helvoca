import { useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  getCommercialQuotes,
  updateCommercialQuoteStatus,
  type CommercialQuote,
  type QuoteStatus
} from "./quotesApi";
import styles from "./OperationsQuotesPanel.module.css";

interface Props {
  canManage: boolean;
}

const labels: Record<QuoteStatus, string> = {
  REQUESTED: "Solicitada",
  READY: "Lista",
  ACCEPTED: "Aceptada",
  REJECTED: "Rechazada",
  CANCELLED: "Cancelada"
};

function formatAmount(quote: CommercialQuote) {
  if (quote.amount == null) return "Monto pendiente";
  try {
    return new Intl.NumberFormat("es-CL", {
      style: "currency",
      currency: quote.currency,
      maximumFractionDigits: quote.currency === "CLP" ? 0 : 2
    }).format(Number(quote.amount));
  } catch {
    return String(quote.amount) + " " + quote.currency;
  }
}

function displayReference(id: string) {
  return id.length > 20 ? `${id.slice(0, 8)}…${id.slice(-6)}` : id;
}

function nextQuoteActions(status: QuoteStatus): Array<{ status: QuoteStatus; label: string }> {
  if (status === "REQUESTED") {
    return [
      { status: "READY", label: "Marcar lista" },
      { status: "CANCELLED", label: "Cancelar" }
    ];
  }
  if (status === "READY") {
    return [
      { status: "ACCEPTED", label: "Marcar aceptada" },
      { status: "REJECTED", label: "Marcar rechazada" },
      { status: "CANCELLED", label: "Cancelar" }
    ];
  }
  return [];
}

export function OperationsQuotesPanel({ canManage }: Props) {
  const quotes = useQuery({
    queryKey: ["commercial", "quotes"],
    queryFn: getCommercialQuotes,
    retry: false,
    refetchOnWindowFocus: false
  });
  const mutationLock = useRef(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [message, setMessage] = useState("");

  async function changeStatus(quote: CommercialQuote, next: QuoteStatus) {
    if (!canManage || mutationLock.current) return;
    mutationLock.current = true;
    setBusyId(quote.id);
    setMessage("");
    try {
      await updateCommercialQuoteStatus(quote.id, next);
      await quotes.refetch();
    } catch {
      setMessage("No pudimos actualizar la cotización. Comprueba su estado actual e inténtalo de nuevo.");
    } finally {
      mutationLock.current = false;
      setBusyId(null);
    }
  }

  return (
    <section className={styles.panel} data-testid="operations-quotes" aria-label="Cotizaciones comerciales">
      <div className={styles.intro}>
        <div>
          <h3>Cotizaciones</h3>
          <p>Solicitudes de presupuesto asociadas a este negocio. No se convierten automáticamente en pedidos.</p>
          <small>Mostrando hasta 100 cotizaciones recientes.</small>
        </div>
        <button type="button" className={styles.refresh} disabled={quotes.isFetching}
          onClick={() => { void quotes.refetch(); }}>
          {quotes.isFetching ? "Actualizando…" : "Actualizar cotizaciones"}
        </button>
      </div>

      {message && <p className={styles.error} role="alert">{message}</p>}
      {quotes.isPending ? (
        <p className={styles.state} role="status">Cargando cotizaciones…</p>
      ) : quotes.isError && !quotes.data ? (
        <div className={styles.state} role="alert">
          <p>No pudimos cargar las cotizaciones.</p>
          <button type="button" className={styles.refresh} onClick={() => { void quotes.refetch(); }}>Reintentar</button>
        </div>
      ) : (
        <>
          {quotes.isRefetchError && (
            <p role="alert" className={styles.error}>No pudimos actualizar las cotizaciones. Se muestran los últimos datos disponibles.</p>
          )}
          {(quotes.data ?? []).length === 0 ? (
            <p className={styles.state}>No hay cotizaciones registradas todavía.</p>
          ) : (
            <div className={styles.list}>
              {(quotes.data ?? []).map(quote => (
                <article className={styles.card} key={quote.id} data-testid={`operations-quote-${quote.id}`}>
                  <div className={styles.head}>
                    <span className={styles.reference} title={quote.id}>Ref. {displayReference(quote.id)}</span>
                    <span className={styles.status} data-status={quote.status}>{labels[quote.status] ?? quote.status}</span>
                  </div>
                  <div className={styles.details}>
                    <div className={styles.main}>
                      <strong>{quote.title}</strong>
                      <span>{quote.contactName || "Cliente sin nombre"}</span>
                      {quote.description && <small>{quote.description}</small>}
                    </div>
                    <strong className={styles.amount}>{formatAmount(quote)}</strong>
                  </div>
                  <div className={styles.footer}>
                    <span>{quote.source === "VOICE" ? "Voz" : quote.source === "WHATSAPP" ? "WhatsApp" : quote.source === "MANUAL" ? "Manual" : "API"}</span>
                    {canManage && nextQuoteActions(quote.status).length > 0 && (
                      <div className={styles.actions}>
                        {nextQuoteActions(quote.status).map(action => (
                          <button key={action.status} type="button"
                            disabled={busyId !== null}
                            onClick={() => { void changeStatus(quote, action.status); }}>
                            {busyId === quote.id ? "Guardando…" : action.label}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  );
}
