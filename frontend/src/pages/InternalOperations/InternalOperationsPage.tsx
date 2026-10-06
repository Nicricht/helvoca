import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Activity,
  AlertTriangle,
  BarChart3,
  CheckCircle2,
  Gauge,
  LockKeyhole,
  Pause,
  Play,
  RefreshCw,
  RotateCcw,
  Save,
  ShieldCheck
} from "lucide-react";
import { AppShell } from "../../components/AppShell/AppShell";
import { getCurrentUser } from "../../features/dashboard/api";
import {
  configurePilotControl,
  getPilotControl,
  getPilotMetrics,
  getPilotPreflight,
  getPilotReadiness,
  transitionPilotControl,
  type PilotControl,
  type PilotMetricPeriod
} from "../../features/internalOperations/api";
import styles from "./InternalOperationsPage.module.css";

type MetricPeriod = "today" | "last7Days";
type ControlAction = "start" | "pause" | "resume" | "complete";

function money(values?: Record<string, number>) {
  const entries = Object.entries(values ?? {});
  if (!entries.length) return "$0";
  return entries
    .map(([currency, value]) => {
      try {
        return new Intl.NumberFormat("es-CL", {
          style: "currency",
          currency,
          maximumFractionDigits: currency === "CLP" ? 0 : 2
        }).format(Number(value || 0));
      } catch {
        return `\${currency} \${Number(value || 0).toLocaleString("es-CL")}`;
      }
    })
    .join(" · ");
}

function pct(value?: number) {
  return `\${Number(value || 0).toLocaleString("es-CL", { maximumFractionDigits: 1 })}%`;
}

function localDateTime(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (part: number) => String(part).padStart(2, "0");
  return `\${date.getFullYear()}-\${pad(date.getMonth() + 1)}-\${pad(date.getDate())}T\${pad(date.getHours())}:\${pad(date.getMinutes())}`;
}

function trafficLabel(mode?: string | null) {
  const labels: Record<string, string> = {
    BLOCKED_GLOBAL: "Tráfico real bloqueado globalmente",
    BLOCKED_TENANT: "Tráfico bloqueado por tenant",
    LIVE_ALLOWED: "Tráfico real habilitado",
    NOT_ENROLLED: "Tenant fuera del control piloto"
  };
  return mode ? labels[mode] ?? mode : "Sin estado";
}

function controlSummary(control: PilotControl) {
  const blockers = control.blockers ?? [];
  if (blockers.length) return `Bloqueos: \${blockers.join(", ")}.`;
  if (control.status === "RUNNING") {
    return "Piloto en ejecución. Las acciones reales siguen sujetas al guard global y al estado del tenant.";
  }
  if (control.status === "PAUSED") return "Piloto pausado. El tenant no debe producir efectos externos reales.";
  if (control.status === "COMPLETED") return "Piloto completado.";
  return "Sin bloqueos. Iniciar el piloto no enciende por sí solo el switch global de efectos externos.";
}

export function InternalOperationsPage() {
  const queryClient = useQueryClient();
  const mutationLock = useRef(false);
  const [period, setPeriod] = useState<MetricPeriod>("today");
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [responsibleName, setResponsibleName] = useState("");
  const [responsibleContact, setResponsibleContact] = useState("");
  const [goal, setGoal] = useState("");
  const [plannedEnd, setPlannedEnd] = useState("");

  const me = useQuery({
    queryKey: ["auth", "me"],
    queryFn: getCurrentUser,
    retry: false
  });

  const allowed =
    me.data?.roles?.includes("BUSINESS_ADMIN") ||
    me.data?.roles?.includes("OPERATOR");
  const canControl = Boolean(me.data?.roles?.includes("BUSINESS_ADMIN"));

  const readiness = useQuery({
    queryKey: ["internal-operations", "pilot-readiness"],
    queryFn: getPilotReadiness,
    retry: false,
    enabled: Boolean(allowed)
  });

  const control = useQuery({
    queryKey: ["internal-operations", "pilot-control"],
    queryFn: getPilotControl,
    retry: false,
    enabled: Boolean(allowed)
  });

  const preflight = useQuery({
    queryKey: ["internal-operations", "pilot-preflight"],
    queryFn: getPilotPreflight,
    retry: false,
    enabled: Boolean(allowed)
  });

  const metrics = useQuery({
    queryKey: ["internal-operations", "pilot-metrics"],
    queryFn: getPilotMetrics,
    retry: false,
    enabled: Boolean(allowed)
  });

  useEffect(() => {
    if (!me.isPending && !allowed) {
      window.location.replace("/");
    }
  }, [allowed, me.isPending]);

  useEffect(() => {
    if (!control.data) return;
    setResponsibleName(control.data.responsibleName ?? "");
    setResponsibleContact(control.data.responsibleContact ?? "");
    setGoal(control.data.goal ?? "");
    setPlannedEnd(localDateTime(control.data.plannedEndAt));
  }, [
    control.data?.responsibleName,
    control.data?.responsibleContact,
    control.data?.goal,
    control.data?.plannedEndAt
  ]);

  const metricData = useMemo<PilotMetricPeriod>(
    () => metrics.data?.[period] ?? {},
    [metrics.data, period]
  );

  async function refreshAll() {
    setFeedback("");
    await Promise.all([
      readiness.refetch(),
      control.refetch(),
      preflight.refetch(),
      metrics.refetch()
    ]);
  }

  async function runControlMutation(action: ControlAction) {
    if (!canControl || mutationLock.current) return;
    mutationLock.current = true;
    setBusy(true);
    setFeedback("");
    try {
      const next = await transitionPilotControl(action);
      queryClient.setQueryData(["internal-operations", "pilot-control"], next);
      setFeedback("Estado del piloto actualizado.");
      await Promise.all([readiness.refetch(), preflight.refetch(), metrics.refetch()]);
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : "No fue posible actualizar el piloto.");
    } finally {
      mutationLock.current = false;
      setBusy(false);
    }
  }

  async function saveConfiguration() {
    if (!canControl || mutationLock.current) return;
    mutationLock.current = true;
    setBusy(true);
    setFeedback("");
    try {
      const end = plannedEnd ? new Date(plannedEnd) : null;
      const next = await configurePilotControl({
        responsibleName: responsibleName.trim(),
        responsibleContact: responsibleContact.trim(),
        goal: goal.trim(),
        plannedEndAt: end && !Number.isNaN(end.getTime()) ? end.toISOString() : null
      });
      queryClient.setQueryData(["internal-operations", "pilot-control"], next);
      setFeedback("Configuración del piloto guardada.");
      await Promise.all([readiness.refetch(), preflight.refetch()]);
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : "No fue posible guardar la configuración.");
    } finally {
      mutationLock.current = false;
      setBusy(false);
    }
  }

  if (me.isPending || !allowed) return null;

  const readinessData = readiness.data;
  const controlData = control.data;
  const preflightData = preflight.data;
  const snapshot = preflightData?.snapshot ?? {};
  const preflightBlockers = preflightData?.blockers ?? [];
  const preflightWarnings = preflightData?.warnings ?? [];
  const conversations =
    Number(metricData.calls || 0) + Number(metricData.whatsappConversations || 0);
  const anyLoading =
    readiness.isPending || control.isPending || preflight.isPending || metrics.isPending;

  return (
    <AppShell>
      <main className={`rv-page-frame \${styles.page}`} data-visual-page="internal-operations">
        <div className={styles.atmosphere} aria-hidden="true">
          <span className={styles.orbCyan} />
          <span className={styles.orbViolet} />
          <span className={styles.grid} />
        </div>

        <header className={styles.header}>
          <div>
            <p className="eyebrow">USO INTERNO</p>
            <h1>Operación y certificación</h1>
            <p>
              Readiness, control del piloto, launch cage y métricas de salud. Esta superficie no gestiona pedidos:
              esa responsabilidad vive en Operaciones / Pedidos.
            </p>
          </div>
          <div className={styles.headerActions}>
            <span className={styles.internalBadge}><LockKeyhole size={14} /> Interno</span>
            <button className="button secondary" type="button" onClick={refreshAll} disabled={busy || anyLoading}>
              <RefreshCw size={15} aria-hidden="true" /> Actualizar
            </button>
          </div>
        </header>

        <section className={styles.safetyBanner} aria-label="Límite de seguridad del piloto">
          <ShieldCheck size={20} aria-hidden="true" />
          <div>
            <strong>GO no significa tráfico real habilitado</strong>
            <span>
              Esta consola nunca enciende el switch global de efectos externos. La decisión final sigue siendo backend-authoritative.
            </span>
          </div>
        </section>

        <section className={styles.card} aria-labelledby="readinessTitle">
          <div className={styles.cardHead}>
            <div>
              <span className={styles.kicker}>PILOTO REAL</span>
              <h2 id="readinessTitle">Preparación para operar</h2>
              <p>Comprueba los componentes críticos antes de una certificación controlada.</p>
            </div>
            <div className={styles.score}>
              <strong data-testid="pilot-readiness-score">
                {readinessData ? `\${Number(readinessData.passed || 0)}/\${Number(readinessData.total || readinessData.checks?.length || 0)}` : "–"}
              </strong>
              <span>COMPONENTES LISTOS</span>
            </div>
          </div>

          {readiness.isError ? (
            <div className={styles.inlineError} role="alert">No pudimos comprobar readiness.</div>
          ) : (
            <div className={styles.checkGrid}>
              {(readinessData?.checks ?? []).map(check => (
                <article key={check.code} className={styles.checkItem} data-ready={check.ready ? "true" : "false"}>
                  <span>{check.ready ? "LISTO" : "PENDIENTE"}</span>
                  <strong>{check.label || check.code}</strong>
                  <small>{check.detail || ""}</small>
                </article>
              ))}
            </div>
          )}

          <div className={styles.cardFoot}>
            <span>
              {readinessData?.ready
                ? "Todo el circuito crítico reportado por backend está listo para certificación."
                : readinessData?.blockers?.length
                  ? `Falta: \${readinessData.blockers.join(", ")}.`
                  : "Esperando la evaluación de readiness."}
            </span>
            <a className="button small ghost" href="/app/settings">Corregir configuración</a>
          </div>
        </section>

        <section className={styles.card} aria-labelledby="controlTitle">
          <div className={styles.cardHead}>
            <div>
              <span className={styles.kicker}>CONTROL OPERATIVO</span>
              <h2 id="controlTitle">Piloto real</h2>
              <p>Responsable, objetivo y ciclo de vida del piloto controlado.</p>
            </div>
            <span className={styles.stateBadge} data-state={controlData?.status || "LOADING"} data-testid="pilot-control-state">
              {controlData?.launchDecision || controlData?.status || "CARGANDO"}
            </span>
          </div>

          {control.isError ? (
            <div className={styles.inlineError} role="alert">No pudimos cargar el control del piloto.</div>
          ) : (
            <>
              <div className={styles.formGrid}>
                <label>
                  <span>Responsable</span>
                  <input
                    value={responsibleName}
                    onChange={event => setResponsibleName(event.target.value)}
                    maxLength={180}
                    disabled={!canControl || busy}
                  />
                </label>
                <label>
                  <span>Contacto</span>
                  <input
                    value={responsibleContact}
                    onChange={event => setResponsibleContact(event.target.value)}
                    maxLength={180}
                    disabled={!canControl || busy}
                  />
                </label>
                <label className={styles.wide}>
                  <span>Objetivo medible</span>
                  <textarea
                    value={goal}
                    onChange={event => setGoal(event.target.value)}
                    disabled={!canControl || busy}
                  />
                </label>
                <label>
                  <span>Cierre planificado</span>
                  <input
                    type="datetime-local"
                    value={plannedEnd}
                    onChange={event => setPlannedEnd(event.target.value)}
                    disabled={!canControl || busy}
                  />
                </label>
              </div>

              <div className={styles.controlActions}>
                {canControl && (
                  <button className="button secondary" type="button" onClick={saveConfiguration} disabled={busy}>
                    <Save size={15} aria-hidden="true" /> Guardar configuración
                  </button>
                )}
                {canControl && controlData?.canStart && (
                  <button className="button primary" type="button" onClick={() => runControlMutation("start")} disabled={busy}>
                    <Play size={15} aria-hidden="true" /> Iniciar piloto
                  </button>
                )}
                {canControl && controlData?.canPause && (
                  <button className="button secondary" type="button" onClick={() => runControlMutation("pause")} disabled={busy}>
                    <Pause size={15} aria-hidden="true" /> Pausar
                  </button>
                )}
                {canControl && controlData?.canResume && (
                  <button className="button primary" type="button" onClick={() => runControlMutation("resume")} disabled={busy}>
                    <RotateCcw size={15} aria-hidden="true" /> Reanudar
                  </button>
                )}
                {canControl && controlData?.canComplete && (
                  <button className="button ghost" type="button" onClick={() => runControlMutation("complete")} disabled={busy}>
                    <CheckCircle2 size={15} aria-hidden="true" /> Completar piloto
                  </button>
                )}
              </div>

              <p className={styles.controlSummary}>
                {controlData ? controlSummary(controlData) : "Cargando estado del piloto…"}
              </p>
              {!canControl && <p className={styles.readOnly}>Modo lectura: solo BUSINESS_ADMIN puede cambiar el ciclo del piloto.</p>}
              {feedback && <div className={styles.feedback} role="status">{feedback}</div>}
            </>
          )}
        </section>

        <section className={styles.card} aria-labelledby="preflightTitle">
          <div className={styles.cardHead}>
            <div>
              <span className={styles.kicker}>Launch cage</span>
              <h2 id="preflightTitle">Preflight del primer negocio</h2>
              <p>Configuración, seguridad, inventario y reconciliación en una sola decisión.</p>
            </div>
            <span
              className={styles.decisionBadge}
              data-decision={preflightData?.decision || "NO_GO"}
              data-testid="pilot-preflight-decision"
            >
              {preflightData?.decision || "CARGANDO"}
            </span>
          </div>

          {preflight.isError ? (
            <div className={styles.inlineError} role="alert">No pudimos evaluar el launch cage.</div>
          ) : (
            <>
              <div className={styles.safetyChips}>
                <span>Piloto: {preflightData?.pilotStatus || "DRAFT"}</span>
                <span
                  data-live={preflightData?.trafficMode === "LIVE_ALLOWED" ? "true" : "false"}
                  data-testid="pilot-preflight-traffic"
                >
                  {trafficLabel(preflightData?.trafficMode)}
                </span>
                <span data-live={preflightData?.globalExternalEffectsEnabled ? "true" : "false"}>
                  Switch global: {preflightData?.globalExternalEffectsEnabled ? "ON" : "OFF"}
                </span>
              </div>

              <div className={styles.snapshotGrid}>
                <article><strong>{Number(snapshot.ordersToday || 0)}</strong><span>Pedidos hoy</span></article>
                <article><strong>{Number(snapshot.paymentAttemptsToday || 0)} / {Number(snapshot.successfulPaymentsToday || 0)}</strong><span>Pagos / exitosos</span></article>
                <article><strong>{Number(snapshot.availableInventoryUnits || 0)}</strong><span>Unidades disponibles</span></article>
                <article><strong>{Number(snapshot.lowStockAlerts || 0) + Number(snapshot.outOfStockAlerts || 0)}</strong><span>Alertas de stock</span></article>
                <article><strong>{Number(snapshot.reconciliationAnomalies || 0)}</strong><span>Anomalías V6</span></article>
              </div>

              <div className={styles.preflightChecks}>
                {(preflightData?.checks ?? []).map(check => (
                  <article key={check.code} data-pass={check.passed ? "true" : "false"}>
                    <strong>{check.passed ? "✓" : "✕"} {check.label || check.code}</strong>
                    <span>{check.detail || ""}</span>
                  </article>
                ))}
              </div>

              <div className={styles.cardFoot}>
                <span>
                  {preflightBlockers.length
                    ? `NO-GO: \${preflightBlockers.join(", ")}`
                    : preflightWarnings.length
                      ? `GO con observaciones: \${preflightWarnings.join(", ")}`
                      : "GO: no hay bloqueos detectados. La activación real sigue requiriendo autorización explícita."}
                </span>
              </div>
            </>
          )}
        </section>

        <section className={styles.card} aria-labelledby="metricsTitle">
          <div className={styles.cardHead}>
            <div>
              <span className={styles.kicker}>RESULTADOS</span>
              <h2 id="metricsTitle">Métricas del piloto</h2>
              <p>Embudo comercial y salud operativa confirmados por backend.</p>
            </div>
            <div className={styles.periodSwitch} role="group" aria-label="Periodo de métricas">
              <button type="button" data-active={period === "today"} onClick={() => setPeriod("today")}>Hoy</button>
              <button type="button" data-active={period === "last7Days"} onClick={() => setPeriod("last7Days")}>7 días</button>
            </div>
          </div>

          {metrics.isError ? (
            <div className={styles.inlineError} role="alert">No pudimos cargar las métricas del piloto.</div>
          ) : (
            <>
              <div className={styles.metricsGrid}>
                <article><Activity size={17} aria-hidden="true" /><strong data-testid="pilot-metric-conversations">{conversations}</strong><span>Conversaciones</span><small>Llamadas + WhatsApp</small></article>
                <article><Gauge size={17} aria-hidden="true" /><strong>{Number(metricData.orders || 0)}</strong><span>Pedidos</span><small>Órdenes no canceladas</small></article>
                <article><CheckCircle2 size={17} aria-hidden="true" /><strong>{Number(metricData.successfulPayments || 0)}</strong><span>Pagos exitosos</span><small>{pct(metricData.paidOrderConversionPct)} de pedidos pagados</small></article>
                <article><BarChart3 size={17} aria-hidden="true" /><strong data-testid="pilot-metric-revenue">{money(metricData.confirmedRevenueByCurrency)}</strong><span>Ingresos confirmados</span><small>Solo pagos SUCCEEDED</small></article>
              </div>
              <div className={styles.healthGrid}>
                <article><strong>{Number(metricData.bookings || 0)}</strong><span>Reservas creadas</span></article>
                <article><strong>{Number(metricData.pendingPayments || 0)}</strong><span>Pagos pendientes</span></article>
                <article><strong>{Number(metricData.failedPayments || 0)}</strong><span>Pagos fallidos</span></article>
                <article><strong>{Number(metricData.humanTransfers || 0)}</strong><span>Derivaciones humanas</span></article>
              </div>
              <p className={styles.metricsFoot}>
                Éxito de pago {pct(metricData.paymentSuccessRatePct)} · Fallas de llamada {pct(metricData.callFailureRatePct)} · Derivación humana {pct(metricData.humanTransferRatePct)}
              </p>
            </>
          )}
        </section>

        {anyLoading && (
          <div className={styles.loading} role="status" aria-live="polite">
            <RefreshCw size={15} aria-hidden="true" /> Sincronizando certificación…
          </div>
        )}

        {(preflightBlockers.length > 0 || readinessData?.ready === false) && (
          <div className={styles.warning} role="status">
            <AlertTriangle size={16} aria-hidden="true" />
            Hay bloqueos pendientes. No abras tráfico real hasta que el backend reporte una condición segura.
          </div>
        )}
      </main>
    </AppShell>
  );
}
