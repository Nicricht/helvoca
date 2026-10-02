import { useQuery } from "@tanstack/react-query";
import { MotionConfig, motion, useReducedMotion } from "framer-motion";
import {
  Activity,
  ArrowUpRight,
  BarChart3,
  CalendarDays,
  CircleHelp,
  ClipboardList,
  MessageSquare,
  PackageSearch,
  Phone,
  Settings,
  ShoppingCart,
  Sparkles,
  TriangleAlert,
  Users,
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
    href: "/settings.html?section=business"
  },
  ADD_SERVICE: {
    text: "Configura tus servicios para que la IA pueda orientar y reservar.",
    href: "/settings.html?section=services"
  },
  CONFIGURE_HOURS: {
    text: "Define tus horarios de atención.",
    href: "/settings.html?section=hours"
  },
  CONNECT_PHONE_NUMBER: {
    text: "Conecta el canal telefónico de tu recepcionista.",
    href: "/settings.html?section=receptionist"
  },
  BUSINESS_PROFILE: {
    text: "Completa la información de tu negocio.",
    href: "/settings.html?section=business"
  },
  SERVICES: {
    text: "Configura tus servicios para que la IA pueda orientar y reservar.",
    href: "/settings.html?section=services"
  },
  SCHEDULE: {
    text: "Define tus horarios de atención.",
    href: "/settings.html?section=hours"
  },
  PHONE: {
    text: "Conecta el canal telefónico de tu recepcionista.",
    href: "/settings.html?section=receptionist"
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

function TodaySummary({ dashboard }: { dashboard: OperationsDashboard }) {
  const quiet =
    dashboard.callsToday === 0 &&
    dashboard.bookingsToday === 0 &&
    dashboard.newCustomersToday === 0;

  const items = [
    { label: "Llamadas", value: dashboard.callsToday, icon: Phone, tone: "cyan" },
    { label: "Reservas", value: dashboard.bookingsToday, icon: CalendarDays, tone: "violet" },
    { label: "Clientes nuevos", value: dashboard.newCustomersToday, icon: Users, tone: "emerald" }
  ] as const;

  return (
    <section className={styles.sectionBlock} aria-labelledby="homeTodayTitle">
      <div className={styles.sectionTitleRow}>
        <div>
          <span className={styles.kicker}>HOY</span>
          <h2 id="homeTodayTitle">Qué está pasando hoy</h2>
        </div>
        <span className={styles.livePill}><span aria-hidden="true" /> En vivo</span>
      </div>

      <div className={styles.summaryGrid}>
        {items.map(({ label, value, icon: Icon, tone }, index) => (
          <motion.article
            key={label}
            className={styles.metricCard}
            data-tone={tone}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: .26, delay: index * .045 }}
          >
            <span className={styles.metricIcon} aria-hidden="true"><Icon size={19} /></span>
            <span className={styles.metricValue}>{value}</span>
            <span className={styles.metricLabel}>{label}</span>
            <span className={styles.metricTrace} aria-hidden="true" />
          </motion.article>
        ))}
      </div>

      {quiet && <p className={styles.emptyHint}>Todavía no hay actividad registrada hoy.</p>}
    </section>
  );
}

function AttentionPanel({ dashboard }: { dashboard: OperationsDashboard }) {
  const alerts = [
    dashboard.openRequests > 0
      ? {
          key: "requests",
          icon: ClipboardList,
          tone: "warning",
          title: `${dashboard.openRequests} solicitudes pendientes`,
          detail: "Revisa las solicitudes que esperan una decisión."
        }
      : null,
    dashboard.unansweredQuestions > 0
      ? {
          key: "questions",
          icon: CircleHelp,
          tone: "violet",
          title: `${dashboard.unansweredQuestions} pregunta${dashboard.unansweredQuestions === 1 ? "" : "s"} sin respuesta`,
          detail: "La IA encontró conocimiento que conviene completar."
        }
      : null,
    dashboard.callFailuresToday > 0
      ? {
          key: "failures",
          icon: TriangleAlert,
          tone: "danger",
          title: `${dashboard.callFailuresToday} fallo${dashboard.callFailuresToday === 1 ? "" : "s"} de llamada`,
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
    <section className={styles.panel} aria-labelledby="homeAttentionTitle">
      <div className={styles.panelHeading}>
        <div>
          <span className={styles.kicker}>PRIORIDAD</span>
          <h2 id="homeAttentionTitle">Necesita tu atención</h2>
        </div>
        {alerts.length > 0 && <span className={styles.countPill}>{alerts.length}</span>}
      </div>

      {alerts.length === 0 ? (
        <div className={styles.calmState}>
          <Sparkles size={21} aria-hidden="true" />
          <div>
            <strong>Todo bajo control</strong>
            <span>Sin pendientes críticos por ahora.</span>
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
              <ArrowUpRight size={16} aria-hidden="true" />
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function RecentActivity({ dashboard }: { dashboard: OperationsDashboard }) {
  const requestItems = dashboard.recentRequests.map(item => ({
    id: `request-${item.id}`,
    title: item.title,
    detail: item.status ? item.status.replaceAll("_", " ") : "Solicitud",
    timestamp: item.createdAt,
    icon: ClipboardList,
    tone: item.priority === "HIGH" ? "warning" : "cyan"
  }));

  const callItems = dashboard.recentCalls.map(item => ({
    id: `call-${item.id}`,
    title: item.callerNumber ? `Llamada ${item.callerNumber}` : "Llamada reciente",
    detail: item.resolution
      ? item.resolution.replaceAll("_", " ")
      : item.status?.replaceAll("_", " ") || "Llamada",
    timestamp: item.startedAt,
    icon: Phone,
    tone: item.status === "FAILED" ? "danger" : "emerald"
  }));

  const activity = [...requestItems, ...callItems]
    .sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp))
    .slice(0, 6);

  return (
    <section className={styles.panel} aria-labelledby="homeRecentTitle">
      <div className={styles.panelHeading}>
        <div>
          <span className={styles.kicker}>TRAZABILIDAD</span>
          <h2 id="homeRecentTitle">Actividad reciente</h2>
        </div>
        <Activity size={18} aria-hidden="true" />
      </div>

      {activity.length === 0 ? (
        <p className={styles.emptyPanel}>Todavía no hay actividad reciente para mostrar.</p>
      ) : (
        <div className={styles.activityList}>
          {activity.map(({ id, title, detail, timestamp, icon: Icon, tone }) => (
            <div key={id} className={styles.activityItem}>
              <span className={styles.activityIcon} data-tone={tone} aria-hidden="true">
                <Icon size={17} />
              </span>
              <div className={styles.activityCopy}>
                <strong>{title}</strong>
                <span>{detail}</span>
              </div>
              <time dateTime={timestamp}>{formatActivityTime(timestamp, dashboard.timezone)}</time>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function QuickActions() {
  const actions = [
    { label: "Agenda", href: "/#bookings", icon: CalendarDays },
    { label: "Clientes", href: "/#customers", icon: Users },
    { label: "Conversaciones", href: "/conversations.html", icon: MessageSquare },
    { label: "Inventario", href: "/app/inventory", icon: PackageSearch },
    { label: "Configuración", href: "/settings.html", icon: Settings },
    { label: "Plan y consumo", href: "/app/plan", icon: WalletCards }
  ];

  return (
    <nav className={styles.quickSection} aria-label="Accesos rápidos">
      <div className={styles.sectionTitleRow}>
        <div>
          <span className={styles.kicker}>ATAJOS</span>
          <h2>Accesos rápidos</h2>
        </div>
      </div>
      <div className={styles.quickGrid}>
        {actions.map(({ label, href, icon: Icon }, index) => (
          <motion.a
            key={label}
            className={styles.quickAction}
            href={href}
            whileHover={{ y: -3 }}
            whileTap={{ scale: .985 }}
            transition={{ duration: .16 }}
          >
            <span className={styles.quickIcon} aria-hidden="true"><Icon size={19} /></span>
            <span>{label}</span>
            <ArrowUpRight size={15} aria-hidden="true" />
          </motion.a>
        ))}
      </div>
    </nav>
  );
}

function SalesSummary({
  data,
  loading,
  failed
}: {
  data?: SalesAnalytics;
  loading: boolean;
  failed: boolean;
}) {
  const totals = data?.currencyTotals?.length
    ? data.currencyTotals
    : data?.primaryCurrency && data.totalRevenue !== null
      ? [{ currency: data.primaryCurrency, amount: data.totalRevenue }]
      : [];

  const multipleCurrencies = totals.length > 1;
  const maxRevenue = Math.max(1, ...(data?.salesOverTime?.map(point => point.revenue) || [0]));

  return (
    <section className={styles.salesSection} aria-labelledby="homeSalesTitle">
      <div className={styles.sectionTitleRow}>
        <div>
          <span className={styles.kicker}>ÚLTIMOS 7 DÍAS</span>
          <h2 id="homeSalesTitle">Resumen de ventas</h2>
        </div>
        <BarChart3 size={18} aria-hidden="true" />
      </div>

      {loading && <div className={styles.inlineLoading} role="status">Cargando resumen de ventas…</div>}

      {failed && (
        <div className={styles.salesError} role="alert">
          No pudimos cargar el resumen de ventas. Tu operación diaria sigue disponible.
        </div>
      )}

      {!loading && !failed && data && (
        <div className={styles.salesGrid}>
          <div className={styles.salesTotal}>
            <span>Ventas confirmadas</span>
            {totals.length === 0 ? (
              <strong>$0</strong>
            ) : multipleCurrencies ? (
              <div className={styles.currencyStack}>
                {totals.map(total => (
                  <strong key={total.currency}>
                    <small>{total.currency}</small> {formatMoney(total.amount, total.currency)}
                  </strong>
                ))}
              </div>
            ) : (
              <strong>{formatMoney(totals[0].amount, totals[0].currency)}</strong>
            )}
            <span className={styles.salesMeta}>
              {data.paidOrders} pedidos pagados
              {data.revenueChangePercent !== null
                ? ` · ${data.revenueChangePercent >= 0 ? "+" : ""}${data.revenueChangePercent.toLocaleString("es-CL")}%`
                : ""}
            </span>
          </div>

          <div className={styles.miniChart} aria-label="Tendencia de ventas de los últimos siete días">
            {(data.salesOverTime || []).map((point, index) => {
              const height = Math.max(8, Math.round((point.revenue / maxRevenue) * 100));
              return (
                <motion.span
                  key={point.date}
                  title={`${point.date}: ${point.revenue}`}
                  initial={{ height: "8%" }}
                  animate={{ height: `${height}%` }}
                  transition={{ duration: .45, delay: index * .045, ease: "easeOut" }}
                />
              );
            })}
          </div>

          <div className={styles.originNote}>
            <ShoppingCart size={18} aria-hidden="true" />
            <div>
              <strong>{data.recepVozOrders} pedidos con origen registrado en RecepVoz</strong>
              <span>Atribución descriptiva por origen Voz / WhatsApp, no una estimación causal.</span>
            </div>
          </div>
        </div>
      )}
    </section>
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
    queryKey: ["home", "sales", 7],
    queryFn: getSalesAnalytics,
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
      <main className={`rv-page-frame ${styles.page}`}>
        <div className={styles.ambient} aria-hidden="true">
          <span className={styles.wave} />
          <span className={styles.orb} />
        </div>

        <header className={`rv-page-header ${styles.header}`}>
          <div>
            <p className="eyebrow">OPERACIÓN</p>
            <h1>Inicio</h1>
            <p>Lo importante de tu negocio, en una sola mirada.</p>
          </div>
          <div className={styles.headerBadge}>
            <span aria-hidden="true" />
            RecepVoz activo
          </div>
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
            initial={reduceMotion ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: reduceMotion ? 0 : .24 }}
          >
            <TodaySummary dashboard={operationsQuery.data} />

            <div className={styles.mainGrid}>
              <AttentionPanel dashboard={operationsQuery.data} />
              <RecentActivity dashboard={operationsQuery.data} />
            </div>

            <QuickActions />

            <SalesSummary
              data={salesQuery.data}
              loading={salesQuery.isPending}
              failed={salesQuery.isError}
            />
          </motion.div>
        ) : null}
      </main>
      </MotionConfig>
    </AppShell>
  );
}
