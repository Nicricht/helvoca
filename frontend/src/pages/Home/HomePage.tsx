import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { MotionConfig, motion, useReducedMotion } from "framer-motion";
import {
  Activity,
  ArrowUpRight,
  CalendarDays,
  CircleHelp,
  ClipboardList,
  Clock3,
  Phone,
  ShoppingCart,
  Sparkles,
  TriangleAlert,
  WalletCards
} from "lucide-react";
import { AppShell } from "../../components/AppShell/AppShell";
import {
  getCurrentUser,
  getOnboardingStatus,
  getOperationsDashboard,
  getSalesAnalytics,
  type OnboardingStatus,
  type OperationsDashboard,
  type SalesAnalytics
} from "../../features/dashboard/api";
import styles from "./HomePage.module.css";

const coreOnboardingSteps = [
  { key: "businessProfileConfigured", label: "Información del negocio" },
  { key: "servicesConfigured", label: "Servicios" },
  { key: "scheduleConfigured", label: "Horarios" },
  { key: "phoneConfigured", label: "Recepcionista" }
] as const;

const nextStepCopy: Record<string, { text: string; href: string }> = {
  CONFIGURE_BUSINESS: {
    text: "Completa la información de tu negocio.",
    href: "/app/settings?section=business"
  },
  ADD_SERVICE: {
    text: "Configura tus servicios para que la IA pueda orientar y reservar.",
    href: "/app/settings?section=services"
  },
  CONFIGURE_HOURS: {
    text: "Define tus horarios de atención.",
    href: "/app/settings?section=hours"
  },
  CONNECT_PHONE_NUMBER: {
    text: "Conecta el canal telefónico de tu recepcionista.",
    href: "/app/settings?section=receptionist"
  },
  BUSINESS_PROFILE: {
    text: "Completa la información de tu negocio.",
    href: "/app/settings?section=business"
  },
  SERVICES: {
    text: "Configura tus servicios para que la IA pueda orientar y reservar.",
    href: "/app/settings?section=services"
  },
  SCHEDULE: {
    text: "Define tus horarios de atención.",
    href: "/app/settings?section=hours"
  },
  PHONE: {
    text: "Conecta el canal telefónico de tu recepcionista.",
    href: "/app/settings?section=receptionist"
  }
};

function formatMoney(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat("es-CL", {
      style: "currency",
      currency,
      maximumFractionDigits: currency === "CLP" ? 0 : 2
    }).format(amount);
  } catch {
    return `${currency} ${amount.toLocaleString("es-CL")}`;
  }
}

function formatActivityTime(value: string, timeZone: string): string {
  try {
    return new Intl.DateTimeFormat("es-CL", {
      hour: "2-digit",
      minute: "2-digit",
      timeZone
    }).format(new Date(value));
  } catch {
    return "";
  }
}


function attributedTotals(data?: SalesAnalytics): Array<{ currency: string; amount: number }> {
  if (!data) return [];

  const totals = new Map<string, number>();
  const add = (currency: string | null, amount: number | null) => {
    if (!currency || amount == null) return;
    totals.set(currency, (totals.get(currency) || 0) + amount);
  };

  add(data.primaryCurrency, data.recepVozRevenue);
  add(data.bookingCurrency, data.recepVozBookingRevenue);

  return Array.from(totals, ([currency, amount]) => ({ currency, amount }));
}

function useAnimatedNumber(value: number, reducedMotion: boolean, duration = 900): number {
  const previous = useRef(0);
  const [display, setDisplay] = useState(reducedMotion ? value : 0);

  useEffect(() => {
    const from = previous.current;
    previous.current = value;

    if (reducedMotion) {
      setDisplay(value);
      return;
    }

    let frame = 0;
    const startedAt = performance.now();

    const tick = (now: number) => {
      const raw = Math.min(1, (now - startedAt) / duration);
      const eased = 1 - Math.pow(1 - raw, 3);
      setDisplay(from + (value - from) * eased);
      if (raw < 1) frame = requestAnimationFrame(tick);
    };

    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [duration, reducedMotion, value]);

  return display;
}

function AnimatedMoney({ amount, currency }: { amount: number; currency: string }) {
  const reduceMotion = useReducedMotion();
  const animated = useAnimatedNumber(amount, Boolean(reduceMotion));
  return <>{formatMoney(animated, currency)}</>;
}

function attentionTotal(dashboard: OperationsDashboard): number {
  return dashboard.openRequests + dashboard.unansweredQuestions + dashboard.callFailuresToday;
}

function HeroOverview({ dashboard }: { dashboard: OperationsDashboard }) {
  const pending = attentionTotal(dashboard);
  const liveCall = dashboard.recentCalls.find(call =>
    ["IN_PROGRESS", "RINGING", "ACTIVE"].includes(call.status || "")
  );

  return (
    <motion.section
      className={styles.heroCard}
      aria-label="Estado de tu negocio"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: .42, ease: [0.16, 1, 0.3, 1] }}
    >
      <div className={styles.heroCopy}>
        <span className={styles.heroStatus} data-tone={pending > 0 ? "attention" : "calm"}>
          <i aria-hidden="true" />
          {pending > 0 ? "Necesita tu atención · " + pending + " asuntos" : "Todo bajo control"}
        </span>

        <h2>Tu negocio está siendo atendido.</h2>
        <p>
          RecepVoz atendió <strong>{dashboard.callsToday}</strong> llamadas hoy y creó{" "}
          <strong>{dashboard.bookingsToday}</strong> reservas.
        </p>

        {pending > 0 && (
          <a className={styles.heroAction} href="#home-attention">
            Revisar pendientes <ArrowUpRight size={15} aria-hidden="true" />
          </a>
        )}
      </div>

      <div className={styles.heroVisual} aria-hidden="true">
        <span className={styles.heroAura} />
        <span className={styles.heroOrbit} />
        <span className={styles.heroFloatIcon} data-position="phone"><Phone size={17} /></span>
        <span className={styles.heroFloatIcon} data-position="calendar"><CalendarDays size={17} /></span>
        <img src="/app/assets/home/hero-bot.webp" alt="" />
        {liveCall && (
          <span className={styles.liveChip}>
            <i /><i /><i /><i />
            Atendiendo ahora
          </span>
        )}
      </div>
    </motion.section>
  );
}

function ValueGenerated({
  data,
  loading,
  refreshing,
  failed,
  days,
  onDaysChange
}: {
  data?: SalesAnalytics;
  loading: boolean;
  refreshing: boolean;
  failed: boolean;
  days: number;
  onDaysChange: (days: number) => void;
}) {
  const totals = attributedTotals(data);
  const single = totals.length === 1 ? totals[0] : null;
  const periods = [
    { days: 1, label: "Hoy" },
    { days: 7, label: "7 días" },
    { days: 30, label: "30 días" }
  ] as const;

  return (
    <motion.section
      className={styles.valueCard}
      aria-labelledby="homeValueTitle"
      aria-busy={loading || refreshing}
      initial={{ opacity: 0, y: 9 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: .38, delay: .16, ease: [0.16, 1, 0.3, 1] }}
    >
      <div className={styles.valueTop}>
        <div className={styles.valueTitle}>
          <span className={styles.valueIcon} aria-hidden="true"><WalletCards size={20} /></span>
          <div>
            <span className={styles.kicker}>VALOR COMERCIAL</span>
            <h2 id="homeValueTitle">Valor generado por RecepVoz</h2>
          </div>
        </div>

        <div className={styles.periodTabs} aria-label="Periodo del valor generado">
          {periods.map(period => (
            <button
              key={period.days}
              type="button"
              className={styles.periodButton}
              aria-pressed={days === period.days}
              onClick={() => onDaysChange(period.days)}
            >
              {period.label}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className={styles.inlineLoading} role="status">Calculando valor atribuido…</div>
      ) : failed ? (
        <div className={styles.valueError} role="alert">
          No pudimos cargar el valor atribuido. Tu operación diaria sigue disponible.
        </div>
      ) : data ? (
        <div className={styles.valueBody}>
          <div className={styles.valueAmount}>
            {totals.length === 0 ? (
              <strong>$0</strong>
            ) : single ? (
              <strong><AnimatedMoney amount={single.amount} currency={single.currency} /></strong>
            ) : (
              <div className={styles.moneyStack}>
                {totals.map(total => (
                  <strong key={total.currency}>
                    <small>{total.currency}</small>
                    {formatMoney(total.amount, total.currency)}
                  </strong>
                ))}
              </div>
            )}
            <span>valor comercial atribuido a RecepVoz</span>
            {refreshing && <em>Actualizando…</em>}
          </div>

          <div className={styles.sourceSummary}>
            <div>
              <ShoppingCart size={17} aria-hidden="true" />
              <span><strong>{data.recepVozOrders}</strong> pedidos con origen RecepVoz</span>
            </div>
            <div>
              <CalendarDays size={17} aria-hidden="true" />
              <span><strong>{data.recepVozPaidBookings}</strong> reservas pagadas con origen RecepVoz</span>
            </div>
          </div>

          <p className={styles.valueNote}>
            Atribución por origen registrado en RecepVoz. No representa utilidad neta ni rentabilidad garantizada.
          </p>
        </div>
      ) : null}
    </motion.section>
  );
}

function TodaySummary({ dashboard }: { dashboard: OperationsDashboard }) {
  const managedMinutes = dashboard.callDurationSecondsToday > 0
    ? Math.max(1, Math.round(dashboard.callDurationSecondsToday / 60))
    : 0;

  const items = [
    { label: "Llamadas atendidas", value: dashboard.callsToday, meta: "hoy", icon: Phone, tone: "cyan" },
    { label: "Reservas generadas", value: dashboard.bookingsToday, meta: "hoy", icon: CalendarDays, tone: "violet" },
    { label: "Minutos gestionados por IA", value: managedMinutes, meta: "en llamadas", icon: Clock3, tone: "emerald" }
  ] as const;

  const quiet = dashboard.callsToday === 0 && dashboard.bookingsToday === 0;

  return (
    <section className={styles.todaySection} aria-labelledby="homeTodayTitle">
      <div className={styles.sectionHeading}>
        <h2 id="homeTodayTitle">Resultados de hoy</h2>
      </div>
      <div className={styles.summaryGrid}>
        {items.map(({ label, value, meta, icon: Icon, tone }, index) => (
          <motion.article
            key={label}
            className={styles.metricCard}
            data-tone={tone}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: .3, delay: .38 + index * .07, ease: [0.16, 1, 0.3, 1] }}
          >
            <span className={styles.metricIcon} aria-hidden="true"><Icon size={20} /></span>
            <span className={styles.metricCopy}>
              <strong>{value}</strong>
              <small>{label}</small>
              <em>{meta}</em>
            </span>
            <span className={styles.metricBars} aria-hidden="true">
              <i /><i /><i /><i /><i />
            </span>
          </motion.article>
        ))}
      </div>
      {quiet && <p className={styles.emptyHint}>Todavía no hay actividad registrada hoy.</p>}
    </section>
  );
}

function AttentionPanel({ dashboard }: { dashboard: OperationsDashboard }) {
  const total = attentionTotal(dashboard);
  const alerts = [
    dashboard.openRequests > 0
      ? {
          key: "requests",
          icon: ClipboardList,
          tone: "warning",
          title: dashboard.openRequests + " solicitudes pendientes",
          detail: "Hay solicitudes esperando una decisión."
        }
      : null,
    dashboard.unansweredQuestions > 0
      ? {
          key: "questions",
          icon: CircleHelp,
          tone: "violet",
          title: dashboard.unansweredQuestions + " pregunta" + (dashboard.unansweredQuestions === 1 ? "" : "s") + " sin respuesta",
          detail: "La IA necesita información adicional para responder mejor."
        }
      : null,
    dashboard.callFailuresToday > 0
      ? {
          key: "failures",
          icon: TriangleAlert,
          tone: "danger",
          title: dashboard.callFailuresToday + " fallo" + (dashboard.callFailuresToday === 1 ? "" : "s") + " de llamada",
          detail: "Hay llamadas que no terminaron normalmente."
        }
      : null
  ].filter(Boolean) as Array<{
    key: string;
    icon: typeof ClipboardList;
    tone: string;
    title: string;
    detail: string;
  }>;

  return (
    <motion.section
      id="home-attention"
      className={styles.panel}
      aria-labelledby="homeAttentionTitle"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: .34, delay: .66, ease: [0.16, 1, 0.3, 1] }}
    >
      <div className={styles.panelHeading}>
        <div>
          <span className={styles.kicker}>SOLO LO IMPORTANTE</span>
          <h2 id="homeAttentionTitle">Necesita tu atención</h2>
        </div>
        {total > 0 && <span className={styles.countPill}>{total}</span>}
      </div>

      {alerts.length === 0 ? (
        <div className={styles.calmState}>
          <Sparkles size={20} aria-hidden="true" />
          <div>
            <strong>Todo bajo control</strong>
            <span>No necesitas hacer nada ahora. RecepVoz puede seguir trabajando solo.</span>
          </div>
        </div>
      ) : (
        <div className={styles.attentionList}>
          {alerts.map(({ key, icon: Icon, tone, title, detail }) => (
            <div key={key} className={styles.attentionItem} data-tone={tone}>
              <span className={styles.attentionIcon} aria-hidden="true"><Icon size={18} /></span>
              <div>
                <strong>{title}</strong>
                <span>{detail}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </motion.section>
  );
}

const activityLabels: Record<string, string> = {
  BOOKING_CREATED: "Reserva creada",
  COMPLETED: "Completada",
  FAILED: "Fallida",
  IN_PROGRESS: "En curso",
  INFORMATION_ONLY: "Solo información",
  OPEN: "Abierta",
  PENDING: "Pendiente",
  RESOLVED: "Resuelta"
};

function activityLabel(value: string | null): string {
  if (!value) return "";
  return activityLabels[value] || value.replaceAll("_", " ").toLowerCase();
}

function LatestActivity({ dashboard }: { dashboard: OperationsDashboard }) {
  const requestItems = dashboard.recentRequests.map(item => ({
    id: "request-" + item.id,
    title: item.title,
    detail: activityLabel(item.status) || "Solicitud",
    timestamp: item.createdAt,
    icon: ClipboardList,
    tone: item.priority === "HIGH" ? "warning" : "cyan"
  }));

  const callItems = dashboard.recentCalls.map(item => ({
    id: "call-" + item.id,
    title: item.callerNumber ? "Llamada " + item.callerNumber : "Llamada reciente",
    detail: activityLabel(item.resolution) || activityLabel(item.status) || "Llamada",
    timestamp: item.startedAt,
    icon: Phone,
    tone: item.status === "FAILED" ? "danger" : "emerald"
  }));

  const latest = [...requestItems, ...callItems]
    .sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp))[0];

  return (
    <motion.section
      className={styles.latestPanel}
      aria-labelledby="homeLatestTitle"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: .34, delay: .78, ease: [0.16, 1, 0.3, 1] }}
    >
      <div className={styles.panelHeading}>
        <div>
          <span className={styles.kicker}>AHORA</span>
          <h2 id="homeLatestTitle">Última actividad</h2>
        </div>
        <Activity size={18} aria-hidden="true" />
      </div>

      {!latest ? (
        <p className={styles.emptyPanel}>Todavía no hay actividad reciente para mostrar.</p>
      ) : (
        <motion.div
          key={latest.id}
          className={styles.latestItem}
          initial={{ opacity: 0, x: 8 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: .28 }}
        >
          <span className={styles.activityIcon} data-tone={latest.tone} aria-hidden="true">
            <latest.icon size={18} />
          </span>
          <div className={styles.activityCopy}>
            <strong>{latest.title}</strong>
            <span>{latest.detail}</span>
          </div>
          <time dateTime={latest.timestamp}>{formatActivityTime(latest.timestamp, dashboard.timezone)}</time>
        </motion.div>
      )}
    </motion.section>
  );
}


function OnboardingCard({ status }: { status: OnboardingStatus }) {
  const completed = coreOnboardingSteps.filter(step => status[step.key]).length;
  const percent = Math.round((completed / coreOnboardingSteps.length) * 100);
  const next = nextStepCopy[status.nextStep || ""] || {
    text: "Completa la configuración pendiente para comenzar.",
    href: "/settings.html"
  };

  return (
    <motion.section
      className={styles.onboarding}
      aria-labelledby="homeOnboardingTitle"
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: .3 }}
    >
      <div className={styles.onboardingGlow} aria-hidden="true" />
      <span className={styles.onboardingIcon} aria-hidden="true"><Sparkles size={24} /></span>
      <div className={styles.onboardingContent}>
        <span className={styles.kicker}>PRIMEROS PASOS</span>
        <h2 id="homeOnboardingTitle">Configura tu negocio</h2>
        <p>{next.text}</p>

        <div className={styles.progressCopy}>
          <span>{completed} de {coreOnboardingSteps.length} pasos completos</span>
          <strong>{percent}%</strong>
        </div>
        <div
          className={styles.progressTrack}
          role="progressbar"
          aria-label="Progreso de configuración"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
        >
          <motion.span
            initial={{ width: 0 }}
            animate={{ width: `${percent}%` }}
            transition={{ duration: .55, ease: "easeOut" }}
          />
        </div>

        <div className={styles.onboardingSteps}>
          {coreOnboardingSteps.map(step => (
            <span key={step.key} data-complete={status[step.key] ? "true" : undefined}>
              <i aria-hidden="true" />
              {step.label}
            </span>
          ))}
        </div>

        <a className={styles.continueButton} href={next.href}>
          Continuar configuración <ArrowUpRight size={16} aria-hidden="true" />
        </a>
      </div>
    </motion.section>
  );
}

export function HomePage() {
  const reduceMotion = useReducedMotion();
  const [salesDays, setSalesDays] = useState(7);

  const meQuery = useQuery({
    queryKey: ["home", "me"],
    queryFn: getCurrentUser
  });

  const isAdmin = meQuery.data?.roles?.includes("BUSINESS_ADMIN") ?? false;
  const onboardingQuery = useQuery({
    queryKey: ["home", "onboarding"],
    queryFn: getOnboardingStatus,
    enabled: isAdmin
  });

  const adminReady = !isAdmin || onboardingQuery.data?.readyForCalls === true;
  const readyToLoad = Boolean(meQuery.data) && adminReady;

  const operationsQuery = useQuery({
    queryKey: ["home", "operations"],
    queryFn: getOperationsDashboard,
    enabled: readyToLoad
  });

  const salesQuery = useQuery({
    queryKey: ["home", "sales", salesDays],
    queryFn: () => getSalesAnalytics(salesDays),
    enabled: readyToLoad
  });

  const onboardingIncomplete =
    isAdmin &&
    Boolean(onboardingQuery.data) &&
    onboardingQuery.data?.readyForCalls === false;

  const loading =
    meQuery.isPending ||
    (isAdmin && onboardingQuery.isPending) ||
    (readyToLoad && operationsQuery.isPending);

  return (
    <AppShell>
      <MotionConfig reducedMotion="user">
      <main className={`rv-page-frame ${styles.page}`} data-visual-page="home">
        <div className={styles.ambient} aria-hidden="true">
          <span className={styles.wave} />
          <span className={styles.orb} />
        </div>

        <header className={styles.compactHeader}>
          <h1>Inicio</h1>
        </header>

        {onboardingIncomplete && onboardingQuery.data ? (
          <OnboardingCard status={onboardingQuery.data} />
        ) : loading ? (
          <div className={styles.loadingState} role="status">
            <span className={styles.spinner} aria-hidden="true" />
            <div>
              <strong>Cargando tu negocio…</strong>
              <span>Estamos preparando el resumen operativo.</span>
            </div>
          </div>
        ) : operationsQuery.isError ? (
          <div className={styles.errorState} role="alert">
            <TriangleAlert size={20} aria-hidden="true" />
            <div>
              <strong>No pudimos cargar el resumen operativo.</strong>
              <span>Intenta nuevamente en unos momentos.</span>
            </div>
          </div>
        ) : operationsQuery.data ? (
          <motion.div
            className={styles.dashboard}
            data-home-motion="premium"
            initial={reduceMotion ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: reduceMotion ? 0 : .24 }}
          >
            <HeroOverview dashboard={operationsQuery.data} />

            <ValueGenerated
              data={salesQuery.data}
              loading={salesQuery.isPending}
              refreshing={salesQuery.isFetching && !salesQuery.isPending}
              failed={salesQuery.isError}
              days={salesDays}
              onDaysChange={setSalesDays}
            />

            <TodaySummary dashboard={operationsQuery.data} />

            <div className={styles.mainGrid}>
              <AttentionPanel dashboard={operationsQuery.data} />
              <LatestActivity dashboard={operationsQuery.data} />
            </div>
          </motion.div>
        ) : null}
      </main>
      </MotionConfig>
    </AppShell>
  );
}
