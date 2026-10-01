import { motion, useReducedMotion } from "framer-motion";
import { BarChart3, Crown, MessageSquareText, PhoneCall, ReceiptText, Timer } from "lucide-react";
import { AppShell } from "../../components/AppShell/AppShell";
import { usePlanConsumption, usageMetrics } from "../../features/billing/usePlanConsumption";
import type { Subscription } from "../../features/billing/api";
import styles from "./PlanConsumptionPage.module.css";

const statusLabels: Record<string, string> = {
  ACTIVE: "Activo",
  TRIALING: "Prueba",
  PAST_DUE: "Pago pendiente",
  SUSPENDED: "Suspendido",
  CANCELLED: "Cancelado",
  CANCELED: "Cancelado",
  INACTIVE: "Inactivo"
};

function safeNumber(value: unknown) {
  const number = Number(value ?? 0);
  return Number.isFinite(number) ? Math.max(0, number) : 0;
}

function formatDate(value?: string) {
  if (!value) return "Sin fecha";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Sin fecha";
  return new Intl.DateTimeFormat("es-CL", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC"
  }).format(date);
}

function projection(subscription: Subscription) {
  const used = safeNumber(subscription.usedMinutes);
  const included = safeNumber(subscription.includedMinutes);
  const start = subscription.currentPeriodStart ? new Date(subscription.currentPeriodStart).getTime() : NaN;
  const end = subscription.currentPeriodEnd ? new Date(subscription.currentPeriodEnd).getTime() : NaN;

  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start || included <= 0) return null;

  const now = Date.now();
  const elapsed = Math.min(Math.max(now - start, 0), end - start);
  const ratio = elapsed / (end - start);
  if (ratio < 0.05) return null;

  return {
    projected: Math.max(Math.round(used / ratio), Math.round(used)),
    included
  };
}

function Metric({
  icon,
  value,
  label,
  testId
}: {
  icon: React.ReactNode;
  value: string;
  label: string;
  testId: string;
}) {
  return (
    <article className={styles.metric} data-testid={testId}>
      <span className={styles.metricIcon} aria-hidden="true">{icon}</span>
      <strong className={styles.metricValue}>{value}</strong>
      <span className={styles.metricLabel}>{label}</span>
    </article>
  );
}

export function PlanConsumptionPage() {
  const reduceMotion = useReducedMotion();
  const model = usePlanConsumption();
  const subscription = model.subscription.data;

  const loading = model.subscription.isPending || model.me.isPending;
  const failed = model.subscription.isError;

  if (loading) {
    return (
      <AppShell>
        <main className="rv-page-frame">
          <header className="rv-page-header">
            <div>
              <p className="eyebrow">CUENTA</p>
              <h1>Plan y consumo</h1>
              <p>Tu plan y el consumo de este período.</p>
            </div>
          </header>
          <div className={styles.loadingCard} role="status" aria-live="polite">
            <span className={styles.spinner} aria-hidden="true" />
            <span>Cargando tu plan y consumo…</span>
          </div>
        </main>
      </AppShell>
    );
  }

  if (failed || !subscription) {
    return (
      <AppShell>
        <main className="rv-page-frame">
          <header className="rv-page-header">
            <div>
              <p className="eyebrow">CUENTA</p>
              <h1>Plan y consumo</h1>
              <p>Tu plan y el consumo de este período.</p>
            </div>
          </header>
          <section className={styles.errorCard} role="alert">
            <strong>No pudimos cargar tu plan.</strong>
            <span>Tu cuenta sigue disponible. Intenta nuevamente en unos minutos.</span>
            <div className={styles.errorBilling} data-testid="billing-state">
              Estado de facturación no disponible.
            </div>
          </section>
        </main>
      </AppShell>
    );
  }

  const included = safeNumber(subscription.includedMinutes);
  const used = safeNumber(subscription.usedMinutes);
  const remaining = Math.max(0, included - used);
  const percentFromSubscription = included > 0 ? Math.round((used / included) * 100) : 0;
  const percent = Math.min(
    100,
    Math.max(0, Math.round(model.usageStatus.data?.usagePercent ?? percentFromSubscription))
  );

  const detailsAvailable = model.canViewDetailedUsage && !model.usage.isError;
  const metrics = usageMetrics(model.usage.data, used);
  const projectionData = projection(subscription);

  const planName = subscription.planName || subscription.publicPlanCode || subscription.plan || "Plan";
  const status = statusLabels[String(subscription.status ?? "").toUpperCase()] || "Activo";
  const billingText = subscription.billingProviderConnected
    ? "Facturación conectada."
    : "Pagos automáticos aún no habilitados.";

  return (
    <AppShell>
      <main className={`rv-page-frame ${styles.page}`}>
        <div className={styles.ambient} aria-hidden="true"><span className={styles.wave} /></div>

        <header className="rv-page-header">
          <div>
            <p className="eyebrow">CUENTA</p>
            <h1>Plan y consumo</h1>
            <p>Tu plan y el consumo de este período, sin ruido innecesario.</p>
          </div>
        </header>

        <motion.section
          className={styles.planCard}
          initial={reduceMotion ? false : { opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: reduceMotion ? 0 : .28 }}
          aria-labelledby="planTitle"
        >
          <div className={styles.planTop}>
            <div className={styles.planIdentity}>
              <span className={styles.planKicker}><Crown size={15} aria-hidden="true" />Plan actual</span>
              <div className={styles.planNameRow}>
                <h2 id="planTitle" className={styles.planName} data-testid="plan-name">{planName}</h2>
                <span className={styles.activePill}>{status}</span>
              </div>
            </div>
            <div className={styles.headerActions}>
              <a className="button primary" href="/pricing.html">Cambiar plan</a>
            </div>
          </div>

          <div className={styles.usageHero}>
            <span className={styles.usageLabel}>Minutos de voz utilizados</span>
            <div className={styles.usageNumber} data-testid="plan-usage">
              <strong>{Math.round(used)}</strong>
              <span>de {Math.round(included)} min</span>
            </div>

            <div className={styles.progressMeta}>
              <span>{percent}% utilizado</span>
              <span>{Math.round(remaining)} min disponibles</span>
            </div>
            <div
              className={styles.progressTrack}
              role="progressbar"
              aria-label="Uso de minutos de voz"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={percent}
              data-testid="voice-progress"
            >
              <motion.span
                className={styles.progressBar}
                initial={reduceMotion ? { width: `${percent}%` } : { width: 0 }}
                animate={{ width: `${percent}%` }}
                transition={{ duration: reduceMotion ? 0 : .65, ease: "easeOut" }}
              />
            </div>
          </div>

          <div className={styles.planFooter}>
            <div className={styles.remaining} data-testid="remaining-minutes">
              <strong>{Math.round(remaining)} min disponibles</strong>
              <span>Se renuevan automáticamente con tu período</span>
            </div>
            <span className={styles.renewal}>Renueva {formatDate(subscription.currentPeriodEnd)}</span>
          </div>
        </motion.section>

        <section className={styles.section} aria-labelledby="usageTitle">
          <h2 id="usageTitle" className={styles.sectionHeading}>Uso del período</h2>
          <div className={styles.metrics}>
            <Metric
              icon={<PhoneCall size={18} />}
              value={detailsAvailable ? String(metrics.calls) : "—"}
              label="Llamadas"
              testId="metric-calls"
            />
            <Metric
              icon={<Timer size={18} />}
              value={detailsAvailable ? `${metrics.minutes} min` : `${Math.round(used)} min`}
              label="Voz"
              testId="metric-minutes"
            />
            <Metric
              icon={<MessageSquareText size={18} />}
              value={detailsAvailable ? String(metrics.messages) : "—"}
              label="Mensajes"
              testId="metric-messages"
            />
          </div>
          {!detailsAvailable && (
            <p className={styles.restricted} data-testid="usage-restricted">
              El detalle de llamadas y mensajes está disponible para propietarios y administradores.
            </p>
          )}
        </section>

        <div className={styles.lowerGrid}>
          <motion.section
            className={styles.projectionCard}
            initial={reduceMotion ? false : { opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: reduceMotion ? 0 : .28, delay: reduceMotion ? 0 : .05 }}
            aria-labelledby="projectionTitle"
          >
            <span className={styles.cardEyebrow}><BarChart3 size={13} aria-hidden="true" /> Proyección</span>
            <p id="projectionTitle" className={styles.projectionText} data-testid="projection">
              {projectionData
                ? <>Al ritmo actual usarás aproximadamente {projectionData.projected} de {projectionData.included} min este período.</>
                : <>Aún no hay suficiente información para proyectar este período.</>}
            </p>
          </motion.section>

          <section className={styles.billingCard} aria-labelledby="billingTitle">
            <span className={styles.billingIcon} aria-hidden="true"><ReceiptText size={20} /></span>
            <div>
              <span className={styles.cardEyebrow}>Facturación</span>
              <p id="billingTitle" className={styles.billingText} data-testid="billing-state">{billingText}</p>
            </div>
          </section>
        </div>
      </main>
    </AppShell>
  );
}
