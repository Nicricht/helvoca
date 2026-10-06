import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Activity,
  BarChart3,
  Building2,
  CheckCircle2,
  Copy,
  FlaskConical,
  LogOut,
  Play,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  UserPlus,
  XCircle
} from "lucide-react";
import { clearAccessToken } from "../../api/client";
import { getCurrentUser } from "../../features/dashboard/api";
import {
  convertPlatformDemoToPilot,
  createPlatformDemoProfile,
  getCurrentPlatformDemoSession,
  getPlatformDemoReadiness,
  getPlatformDemoTimeline,
  getPlatformEconomics,
  listPlatformDemoProfiles,
  preparePlatformDemo,
  provisionPlatformBusiness,
  transitionPlatformDemo,
  type PlatformBusinessProvisioningResponse,
  type PlatformDemoProfileRequest,
  type PlatformDemoSession,
  type PlatformDemoTimeline,
  type PlatformEconomics
} from "../../features/platform/api";
import styles from "./PlatformPage.module.css";

const READINESS = [
  ["runtime", "Runtime demo"],
  ["voiceNumber", "Número de voz"],
  ["voiceAi", "IA de voz"],
  ["businessData", "Datos del negocio"],
  ["operations", "Operaciones"],
  ["whatsapp", "WhatsApp"],
  ["payment", "Pago"],
  ["externalEffects", "Efectos externos"]
] as const;

const CAPABILITIES = ["ORDER", "BOOKING", "QUOTE", "LEAD", "REQUEST", "DELIVERY"];

function clp(value: unknown) {
  const number = Number(value || 0);
  return "$" + new Intl.NumberFormat("es-CL", { maximumFractionDigits: 0 }).format(number);
}

function usd(value: unknown) {
  const number = Number(value || 0);
  return "US$ " + new Intl.NumberFormat("es-CL", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(number);
}

function sessionHint(state: string) {
  if (state === "READY") return "Esperando llamada al número DEMO. La sesión se activará automáticamente cuando llegue una llamada inbound segura.";
  if (state === "ACTIVE") return "Llamada detectada. La sesión está activa y la evidencia persistida se actualiza automáticamente.";
  if (state === "FINISHED") return "La llamada terminó y la sesión se cerró automáticamente. Revisa el Proof of Value.";
  if (state === "FAILED") return "La preparación falló de forma cerrada. Revisa el readiness antes de reintentar.";
  if (state === "ABORTED") return "La demo fue abortada. No se ejecutarán más transiciones para esta sesión.";
  return "Sesión con evidencia server-owned. Los efectos externos siguen desarmados y los pagos en sandbox.";
}

export function PlatformPage() {
  const queryClient = useQueryClient();
  const mutationLock = useRef(false);
  const [demoFormOpen, setDemoFormOpen] = useState(false);
  const [demoMessage, setDemoMessage] = useState("");
  const [sessionMessage, setSessionMessage] = useState("");
  const [provisionMessage, setProvisionMessage] = useState("");
  const [provisionResult, setProvisionResult] = useState<PlatformBusinessProvisioningResponse | null>(null);
  const [busy, setBusy] = useState("");

  const me = useQuery({
    queryKey: ["auth", "me"],
    queryFn: getCurrentUser,
    retry: false,
    staleTime: 60_000
  });

  const platformAdmin = Boolean(me.data?.roles?.includes("PLATFORM_ADMIN"));

  useEffect(() => {
    if (!me.isPending && me.data && !platformAdmin) {
      window.location.replace("/");
    }
  }, [me.isPending, me.data, platformAdmin]);

  const readiness = useQuery({
    queryKey: ["platform", "demo-readiness"],
    queryFn: getPlatformDemoReadiness,
    retry: false,
    enabled: platformAdmin
  });

  const profiles = useQuery({
    queryKey: ["platform", "demo-profiles"],
    queryFn: listPlatformDemoProfiles,
    retry: false,
    enabled: platformAdmin
  });

  const current = useQuery({
    queryKey: ["platform", "demo-session", "current"],
    queryFn: getCurrentPlatformDemoSession,
    retry: false,
    enabled: platformAdmin,
    refetchInterval: platformAdmin ? 1500 : false
  });

  const timeline = useQuery({
    queryKey: ["platform", "demo-timeline", current.data?.id || "none"],
    queryFn: () => getPlatformDemoTimeline(String(current.data?.id)),
    retry: false,
    enabled: platformAdmin && Boolean(current.data?.id),
    refetchInterval: platformAdmin && current.data?.id ? 1500 : false
  });

  const economics = useQuery({
    queryKey: ["platform", "economics"],
    queryFn: getPlatformEconomics,
    retry: false,
    enabled: platformAdmin
  });

  const currentState = String(current.data?.state || "").toUpperCase();
  const canStart = currentState === "READY";
  const canFinish = currentState === "ACTIVE";
  const canAbort = currentState === "READY" || currentState === "ACTIVE";
  const canConvert =
    Boolean(current.data?.id) &&
    !current.data?.convertedPilotBusinessId &&
    (currentState === "READY" || currentState === "FINISHED");

  const portfolio = economics.data || {};
  const businesses = Array.isArray(portfolio.businesses) ? portfolio.businesses : [];
  const providers = Array.isArray(portfolio.providers) ? portfolio.providers : [];

  const platformCost = portfolio.estimatedPlatformCostClp == null
    ? usd(portfolio.estimatedPlatformCostUsd)
    : clp(portfolio.estimatedPlatformCostClp);
  const grossMargin = portfolio.estimatedGrossMarginClp == null
    ? "Configurar USD/CLP"
    : clp(portfolio.estimatedGrossMarginClp);
  const grossMarginPercent = portfolio.estimatedGrossMarginPercent == null
    ? "No disponible"
    : Number(portfolio.estimatedGrossMarginPercent).toFixed(1) + "%";

  const economicsNote = useMemo(() => {
    const pieces: string[] = [];
    const unknown = Number(portfolio.businessesWithUnknownCommercialValue || 0);
    const rate = Number(portfolio.usdToClpRate || 0);
    if (unknown > 0) {
      pieces.push(unknown + " negocio(s) tienen valor comercial personalizado y no se incluyen como ingreso estimado automático.");
    }
    pieces.push(
      rate > 0
        ? "Conversión operativa usada: " + new Intl.NumberFormat("es-CL").format(rate) + " CLP/USD."
        : "Configura HELVOCA_COST_USD_TO_CLP para convertir costos USD y calcular margen CLP."
    );
    return pieces.join(" ");
  }, [portfolio.businessesWithUnknownCommercialValue, portfolio.usdToClpRate]);

  if (me.isPending || !platformAdmin) return null;

  function logout() {
    clearAccessToken();
    window.location.assign("/");
  }

  async function prepareDemo(profileId: string) {
    if (mutationLock.current) return;
    mutationLock.current = true;
    setBusy("prepare:" + profileId);
    setSessionMessage("");
    try {
      const next = await preparePlatformDemo(profileId);
      queryClient.setQueryData(["platform", "demo-session", "current"], next);
      if (next.readiness) queryClient.setQueryData(["platform", "demo-readiness"], next.readiness);
      await timeline.refetch();
    } catch (value) {
      setSessionMessage(value instanceof Error ? value.message : "No fue posible preparar la demo.");
    } finally {
      mutationLock.current = false;
      setBusy("");
    }
  }

  async function transition(action: "start" | "finish" | "abort") {
    if (!current.data?.id || mutationLock.current) return;
    mutationLock.current = true;
    setBusy("transition:" + action);
    setSessionMessage("");
    try {
      const next = await transitionPlatformDemo(current.data.id, action);
      queryClient.setQueryData(["platform", "demo-session", "current"], next);
      if (next.readiness) queryClient.setQueryData(["platform", "demo-readiness"], next.readiness);
      await timeline.refetch();
    } catch (value) {
      setSessionMessage(value instanceof Error ? value.message : "No fue posible cambiar el estado de la demo.");
    } finally {
      mutationLock.current = false;
      setBusy("");
    }
  }

  async function createDemo(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mutationLock.current) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    const payload: PlatformDemoProfileRequest = {
      displayName: String(data.get("demoDisplayName") || "").trim(),
      businessName: String(data.get("demoBusinessName") || "").trim(),
      timezone: String(data.get("demoTimezone") || "America/Santiago").trim(),
      language: String(data.get("demoLanguage") || "es").trim(),
      catalog: {},
      hours: {},
      knowledge: {},
      greeting: String(data.get("demoGreeting") || "").trim(),
      instructions: String(data.get("demoInstructions") || "").trim() || null,
      capabilities: data.getAll("demoCapabilities").map(String),
      presenterNotes: String(data.get("demoPresenterNotes") || "").trim() || null,
      sourceMetadata: { source: "manual" }
    };

    mutationLock.current = true;
    setBusy("create-demo");
    setDemoMessage("");
    try {
      await createPlatformDemoProfile(payload);
      await profiles.refetch();
      form.reset();
      const timezone = form.elements.namedItem("demoTimezone") as HTMLInputElement | null;
      const language = form.elements.namedItem("demoLanguage") as HTMLSelectElement | null;
      if (timezone) timezone.value = "America/Santiago";
      if (language) language.value = "es";
      setDemoMessage("Demo guardada. Ya puedes seguir completando su configuración aprobada.");
    } catch (value) {
      setDemoMessage(value instanceof Error ? value.message : "No fue posible guardar la demo.");
    } finally {
      mutationLock.current = false;
      setBusy("");
    }
  }

  async function convertDemo(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!current.data?.id || mutationLock.current) return;
    const data = new FormData(event.currentTarget);
    mutationLock.current = true;
    setBusy("convert");
    setSessionMessage("");
    try {
      const converted = await convertPlatformDemoToPilot(current.data.id, {
        adminName: String(data.get("pilotAdminName") || "").trim(),
        adminEmail: String(data.get("pilotAdminEmail") || "").trim()
      });
      const next: PlatformDemoSession = {
        ...current.data,
        convertedPilotBusinessId: converted.pilotBusinessId
      };
      queryClient.setQueryData(["platform", "demo-session", "current"], next);
      setSessionMessage(
        "PILOT creado: " + (converted.pilotBusinessName || "Negocio") + " · " +
        converted.pilotBusinessId +
        (converted.invitePath ? " · Invitación de un solo uso generada." : "")
      );
      await timeline.refetch();
    } catch (value) {
      setSessionMessage(value instanceof Error ? value.message : "No fue posible convertir la demo a PILOT.");
    } finally {
      mutationLock.current = false;
      setBusy("");
    }
  }

  async function provision(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mutationLock.current) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    mutationLock.current = true;
    setBusy("provision");
    setProvisionMessage("");
    try {
      const created = await provisionPlatformBusiness({
        businessName: String(data.get("businessName") || "").trim(),
        timezone: String(data.get("timezone") || "").trim(),
        language: String(data.get("language") || "es").trim(),
        humanTransferPhone: String(data.get("humanTransferPhone") || "").trim() || null,
        adminName: String(data.get("adminName") || "").trim(),
        adminEmail: String(data.get("adminEmail") || "").trim()
      });
      setProvisionResult(created);
      setProvisionMessage("Negocio creado. Comparte la invitación con el administrador inicial.");
      form.reset();
      const timezone = form.elements.namedItem("timezone") as HTMLInputElement | null;
      const language = form.elements.namedItem("language") as HTMLSelectElement | null;
      if (timezone) timezone.value = Intl.DateTimeFormat().resolvedOptions().timeZone || "America/Santiago";
      if (language) language.value = "es";
    } catch (value) {
      setProvisionMessage(value instanceof Error ? value.message : "No fue posible crear el negocio.");
    } finally {
      mutationLock.current = false;
      setBusy("");
    }
  }

  async function copyInvite() {
    if (!provisionResult?.invitePath) return;
    const value = new URL(provisionResult.invitePath, window.location.origin).href;
    try {
      await navigator.clipboard.writeText(value);
      setProvisionMessage("Enlace de invitación copiado.");
    } catch {
      setProvisionMessage("El enlace está listo para copiar.");
    }
  }

  return (
    <div className={styles.shell} data-react-platform-console="true">
      <div className={styles.ambient} aria-hidden="true" />
      <header className={styles.topbar}>
        <div className={styles.brand}>
          <span className={styles.brandMark}><Sparkles size={18} aria-hidden="true" /></span>
          <span>
            <strong>RecepVoz Platform</strong>
            <small>PLATFORM ADMIN</small>
          </span>
        </div>
        <div className={styles.topActions}>
          <span className={styles.systemScope}><ShieldCheck size={14} aria-hidden="true" /> SYSTEM scope</span>
          <button id="platformLogout" className={styles.ghostButton} type="button" onClick={logout}>
            <LogOut size={15} aria-hidden="true" /> Salir
          </button>
        </div>
      </header>

      <main className={styles.page}>
        <section className={styles.hero}>
          <div>
            <p className={styles.eyebrow}>PLATFORM ADMIN</p>
            <h1>Control de plataforma</h1>
            <p>Demos, alta de clientes y economía interna en una superficie separada de las cuentas de negocio.</p>
          </div>
          <div className={styles.heroTruth}>
            <ShieldCheck size={18} aria-hidden="true" />
            <span><strong>Frontera explícita</strong> Esta consola opera a nivel plataforma. No representa un tenant comercial.</span>
          </div>
        </section>

        <nav className={styles.sectionNav} aria-label="Secciones de plataforma">
          <a href="#platformDemos"><FlaskConical size={14} /> Demos</a>
          <a href="#platformProvision"><Building2 size={14} /> Clientes</a>
          <a href="#platformEconomics"><BarChart3 size={14} /> Administración</a>
        </nav>

        <section id="platformDemos" className={styles.card} aria-labelledby="platformDemosTitle">
          <div className={styles.sectionHead}>
            <div>
              <p className={styles.eyebrow}>DEMOS</p>
              <h2 id="platformDemosTitle">Centro de Demos</h2>
              <p>Prepara una demo aislada, revisa evidencia real y convierte únicamente configuración aprobada en un PILOT nuevo.</p>
            </div>
            <button id="platformDemoCreateOpen" className={styles.primaryButton} type="button" onClick={() => setDemoFormOpen(true)}>
              + Crear nueva demo
            </button>
          </div>

          <div id="platformDemoReadinessState" className={styles.muted} role="status" aria-live="polite">
            {readiness.isPending ? "Revisando readiness del runtime demo…" : readiness.isError ? "Readiness no disponible." : ""}
          </div>
          {readiness.data && (
            <div id="platformDemoReadiness" className={styles.readinessGrid} aria-label="Readiness del runtime demo">
              {READINESS.map(([key, label]) => {
                const item = readiness.data?.[key];
                const state = String(item?.state || "NOT_CONFIGURED").toUpperCase();
                return (
                  <article key={key} className={styles.readinessCard} data-state={state.toLowerCase()}>
                    <span>{label}</span>
                    <strong>{state}</strong>
                    <small>{item?.detail || ""}</small>
                  </article>
                );
              })}
            </div>
          )}

          <div id="platformDemoSessionState" className={styles.sessionState} role="status" aria-live="polite">
            {current.data
              ? sessionHint(currentState)
              : "Ninguna demo está preparada. Elige un perfil y prepara el runtime antes de la llamada."}
          </div>

          {current.data && (
            <div id="platformDemoSession" className={styles.sessionPanel} aria-label="Sesión live demo actual">
              <div className={styles.sessionFacts}>
                <article><span>Modo</span><strong className={styles.demoPill}>DEMO</strong></article>
                <article><span>Sesión</span><strong>{currentState}</strong></article>
                <article><span>Perfil activo</span><strong>{current.data.demoProfileId || "—"}</strong></article>
                <article><span>Runtime</span><strong>{current.data.runtimeBusinessId || "—"}</strong></article>
                <article><span>Correlación</span><strong>{current.data.correlationId || "—"}</strong></article>
                <article><span>Efectos externos</span><strong>{current.data.externalEffectsState || "DISARMED"}</strong></article>
                <article><span>Pagos</span><strong>{current.data.paymentState || "SANDBOX_ONLY"}</strong></article>
                {current.data.failureReason && <article><span>Bloqueo</span><strong>{current.data.failureReason}</strong></article>}
                {current.data.convertedPilotBusinessId && (
                  <article><span>Conversión</span><strong>PILOT · {current.data.convertedPilotBusinessId}</strong></article>
                )}
              </div>

              <details className={styles.manualControls}>
                <summary>Controles manuales de respaldo</summary>
                <div>
                  {canStart && <button type="button" onClick={() => transition("start")} disabled={busy !== ""}><Play size={14} /> Iniciar manualmente</button>}
                  {canFinish && <button type="button" onClick={() => transition("finish")} disabled={busy !== ""}>Finalizar manualmente</button>}
                  {canAbort && <button type="button" onClick={() => transition("abort")} disabled={busy !== ""}><XCircle size={14} /> Abortar</button>}
                  <button type="button" onClick={() => timeline.refetch()}><RefreshCw size={14} /> Actualizar evidencia</button>
                </div>
              </details>

              <TimelineView timeline={timeline.data || null} />

              {canConvert && (
                <form id="platformDemoConvertForm" className={styles.inlineForm} onSubmit={convertDemo}>
                  <label>Nombre del dueño
                    <input name="pilotAdminName" maxLength={150} required placeholder="Ana Pérez" />
                  </label>
                  <label>Email del dueño
                    <input name="pilotAdminEmail" type="email" maxLength={180} required placeholder="ana@negocio.cl" />
                  </label>
                  <button className={styles.primaryButton} type="submit" disabled={busy !== ""}>
                    Crear PILOT e invitación
                  </button>
                </form>
              )}

              <div id="platformDemoSessionMessage" className={styles.message} role="status">{sessionMessage}</div>
            </div>
          )}

          <div id="platformDemoState" className={styles.muted} role="status" aria-live="polite">
            {profiles.isPending ? "Cargando perfiles de demo…" : profiles.isError ? "No fue posible cargar los perfiles de demo." : ""}
          </div>

          <div id="platformDemoProfiles" className={styles.profileGrid}>
            {(profiles.data || []).map(profile => (
              <article key={profile.id} className={styles.profileCard}>
                <div className={styles.profileTop}>
                  <div>
                    <h3>{profile.displayName || "Demo"}</h3>
                    <p>{profile.businessName || "Negocio demo"} · {profile.language || "es"} · {profile.timezone || ""}</p>
                  </div>
                  <span className={styles.demoPill}>DEMO</span>
                </div>
                <div className={styles.capabilityList}>
                  {(profile.capabilities || []).length
                    ? profile.capabilities.map(capability => <span key={capability}>{capability}</span>)
                    : <span>Sin capacidades activadas</span>}
                </div>
                {profile.presenterNotes && <p><strong>Nota:</strong> {profile.presenterNotes}</p>}
                <button
                  className={styles.primaryButton}
                  type="button"
                  onClick={() => prepareDemo(profile.id)}
                  disabled={busy !== ""}
                >
                  {busy === "prepare:" + profile.id ? "Preparando…" : "Preparar demo"}
                </button>
              </article>
            ))}
            {!profiles.isPending && !profiles.isError && !(profiles.data || []).length && (
              <div className={styles.empty}>Todavía no hay perfiles. Crea uno para preparar una demo con datos aprobados.</div>
            )}
          </div>

          {demoFormOpen && (
            <div className={styles.demoFormWrap}>
              <div className={styles.subhead}>
                <div><h3>Nueva demo</h3><p>No agregues credenciales ni datos productivos sensibles.</p></div>
              </div>
              <form id="platformDemoForm" className={styles.formGrid} onSubmit={createDemo}>
                <label>Nombre de la demo
                  <input name="demoDisplayName" maxLength={150} required placeholder="Sushi Akira · DEMO" />
                </label>
                <label>Nombre que usará la IA
                  <input name="demoBusinessName" maxLength={150} required placeholder="Sushi Akira" />
                </label>
                <label>Zona horaria
                  <input name="demoTimezone" maxLength={60} required defaultValue="America/Santiago" />
                </label>
                <label>Idioma
                  <select name="demoLanguage" required defaultValue="es">
                    <option value="es">Español</option>
                    <option value="en">English</option>
                  </select>
                </label>
                <label className={styles.wide}>Saludo de la IA
                  <textarea name="demoGreeting" maxLength={4000} required placeholder="Hola, gracias por llamar. ¿En qué te ayudo?" />
                </label>
                <label className={styles.wide}>Reglas e instrucciones
                  <textarea name="demoInstructions" maxLength={12000} placeholder="No inventes productos, precios ni disponibilidad." />
                </label>
                <div className={styles.capabilityChecks} aria-label="Capacidades de la demo">
                  {CAPABILITIES.map(capability => (
                    <label key={capability}><input type="checkbox" name="demoCapabilities" value={capability} />{capability}</label>
                  ))}
                </div>
                <label className={styles.wide}>Notas del presentador
                  <textarea name="demoPresenterNotes" maxLength={4000} placeholder="Guion opcional para el presentador." />
                </label>
                <div className={styles.formActions}>
                  <button className={styles.ghostButton} type="button" onClick={() => setDemoFormOpen(false)}>Cancelar</button>
                  <button id="platformDemoSave" className={styles.primaryButton} type="submit" disabled={busy !== ""}>
                    {busy === "create-demo" ? "Guardando…" : "Guardar demo"}
                  </button>
                </div>
              </form>
              <div id="platformDemoMessage" className={styles.message} role="status">{demoMessage}</div>
            </div>
          )}

          <div className={styles.truthBox}>
            <strong>Regla:</strong> DEMO, PILOT y CUSTOMER nunca son el mismo tenant. La conversión crea un PILOT nuevo y no copia llamadas, mensajes, historial, credenciales ni identidades de proveedor. Pagos siguen en SANDBOX_ONLY y efectos externos en DISARMED.
          </div>
        </section>

        <section id="platformProvision" className={styles.sectionBlock} aria-labelledby="platformProvisionTitle">
          <div className={styles.sectionHead}>
            <div>
              <p className={styles.eyebrow}>CLIENTES</p>
              <h2 id="platformProvisionTitle">Alta asistida de negocios</h2>
              <p>Crea el tenant real y entrega el acceso inicial al dueño o administrador.</p>
            </div>
          </div>

          <div className={styles.twoCol}>
            <article className={styles.card}>
              <h3>Nuevo negocio</h3>
              <p>Se crea un trial BASIC y una invitación de administrador. La contraseña la define el cliente al aceptar.</p>
              <form id="platformProvisionForm" className={styles.formGrid} onSubmit={provision}>
                <label className={styles.wide}>Nombre del negocio
                  <input name="businessName" maxLength={150} required placeholder="Clínica Norte" />
                </label>
                <label>Zona horaria
                  <input name="timezone" maxLength={60} required defaultValue="America/Santiago" />
                </label>
                <label>Idioma
                  <select name="language" required defaultValue="es">
                    <option value="es">Español</option>
                    <option value="en">English</option>
                  </select>
                </label>
                <label className={styles.wide}>Teléfono de transferencia humana
                  <input name="humanTransferPhone" maxLength={20} placeholder="+56911112222" />
                </label>
                <label>Nombre del administrador
                  <input name="adminName" maxLength={150} required placeholder="Ana Pérez" />
                </label>
                <label>Email del administrador
                  <input name="adminEmail" type="email" maxLength={180} required placeholder="ana@negocio.cl" />
                </label>
                <div className={styles.formActions}>
                  <button id="platformProvisionSubmit" className={styles.primaryButton} type="submit" disabled={busy !== ""}>
                    {busy === "provision" ? "Creando…" : "Crear negocio e invitación"}
                  </button>
                </div>
              </form>
              <div id="platformProvisionMessage" className={styles.message} role="status">{provisionMessage}</div>
            </article>

            <article className={styles.card}>
              <h3>Entrega al cliente</h3>
              <p>Comparte el enlace una sola vez. Al aceptarlo, el administrador entra al mismo tenant y continúa la ruta de activación.</p>
              {!provisionResult ? (
                <div id="platformProvisionEmpty" className={styles.empty}>Crea un negocio para generar su acceso inicial.</div>
              ) : (
                <div id="platformProvisionResult" className={styles.resultList}>
                  <article><span>Negocio</span><strong id="platformResultBusiness">{provisionResult.businessName} · {provisionResult.businessId}</strong></article>
                  <article><span>Administrador</span><strong id="platformResultAdmin">{provisionResult.adminName} · {provisionResult.adminEmail}</strong></article>
                  <article><span>Estado</span><strong id="platformResultStatus">{provisionResult.invitationStatus === "PENDING" ? "Tenant creado · invitación pendiente" : provisionResult.invitationStatus}</strong></article>
                  <article>
                    <span>Invitación de un solo uso</span>
                    <div className={styles.copyRow}>
                      <input
                        id="platformInviteUrl"
                        readOnly
                        aria-label="Enlace de invitación del administrador"
                        value={new URL(provisionResult.invitePath, window.location.origin).href}
                      />
                      <button id="platformCopyInvite" className={styles.ghostButton} type="button" onClick={copyInvite}>
                        <Copy size={14} /> Copiar
                      </button>
                    </div>
                  </article>
                  <article><span>Siguiente paso</span><strong>El administrador acepta la invitación y continúa el onboarding dentro de RecepVoz.</strong></article>
                </div>
              )}
            </article>
          </div>
        </section>

        <section id="platformEconomics" className={styles.card} aria-labelledby="platformEconomicsTitle">
          <div className={styles.sectionHead}>
            <div>
              <p className={styles.eyebrow}>ADMINISTRACIÓN</p>
              <h2 id="platformEconomicsTitle">Radar económico</h2>
              <p>Estimaciones operativas para controlar costo y margen. No equivalen a pagos cobrados ni a facturas de proveedores.</p>
            </div>
            <button id="platformEconomicsRefresh" className={styles.ghostButton} type="button" onClick={() => economics.refetch()}>
              <RefreshCw size={14} /> Actualizar
            </button>
          </div>

          <div id="platformEconomicsState" className={styles.muted} role="status" aria-live="polite">
            {economics.isPending ? "Cargando economía del período…" : economics.isError ? "Economía no disponible." : ""}
          </div>

          {economics.data && (
            <div id="platformEconomicsContent">
              <div className={styles.kpis}>
                <article><span>Valor comercial estimado</span><strong id="platformCommercialValue">{clp(portfolio.estimatedCommercialValueClp)}</strong></article>
                <article><span>Costo plataforma estimado</span><strong id="platformPlatformCost">{platformCost}</strong></article>
                <article><span>Margen bruto estimado</span><strong id="platformGrossMargin">{grossMargin}</strong></article>
                <article><span>Margen estimado</span><strong id="platformGrossMarginPercent">{grossMarginPercent}</strong></article>
              </div>

              <div className={styles.economicsGrid}>
                <div>
                  <h3>Negocios</h3>
                  <div id="platformEconomicsBusinesses" className={styles.economicsList}>
                    {businesses.length ? businesses.map(item => (
                      <article key={item.businessId} className={styles.economicsRow}>
                        <strong>{item.businessName || "Negocio"} <span>{item.usageAlertLevel || "NORMAL"}</span></strong>
                        <span>{item.planName || item.planCode || "Sin plan"} · {Number(item.usedMinutes || 0)}/{Number(item.includedMinutes || 0)} min</span>
                        <span>{item.estimatedCommercialValueClp == null ? "Valor personalizado" : clp(item.estimatedCommercialValueClp)}</span>
                        <span>{item.estimatedGrossMarginClp == null ? "Margen no disponible" : clp(item.estimatedGrossMarginClp)}</span>
                      </article>
                    )) : <div className={styles.empty}>Aún no hay negocios con economía de período disponible.</div>}
                  </div>
                </div>

                <div>
                  <h3>Proveedores</h3>
                  <div id="platformEconomicsProviders" className={styles.providerList}>
                    {providers.length ? providers.map((item, index) => (
                      <article key={item.aiProvider + "-" + item.aiModel + "-" + index}>
                        <strong>{item.aiProvider || "unknown"} · {item.aiModel || "unknown"}</strong>
                        <small>{Number(item.callCount || 0)} llamadas · {Number(item.minutes || 0)} min</small>
                        <span>{item.estimatedTotalCostClp == null ? usd(item.estimatedTotalCostUsd) : clp(item.estimatedTotalCostClp)} estimados</span>
                      </article>
                    )) : <div className={styles.empty}>Aún no hay llamadas de voz terminadas en los períodos actuales.</div>}
                  </div>
                </div>
              </div>
              <p id="platformEconomicsNote" className={styles.economicsNote}>{economicsNote}</p>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}

function TimelineView({ timeline }: { timeline: PlatformDemoTimeline | null }) {
  const events = timeline?.events || [];
  const proof = timeline?.proofOfValue;
  return (
    <>
      <div id="platformDemoTimeline" className={styles.timeline}>
        {events.length ? events.map((event, index) => (
          <article key={event.type + "-" + event.at + "-" + index}>
            <strong>{event.type || "EVENTO"} · {event.status || "REGISTRADO"}</strong>
            <p>{event.detail || "Evidencia persistida"}</p>
          </article>
        )) : <div className={styles.empty}>Todavía no hay evidencia persistida para esta sesión.</div>}
      </div>
      {proof && (
        <div id="platformDemoProof" className={styles.proof}>
          <strong>Proof of Value · {proof.state || "REVIEW_REQUIRED"}</strong>
          <p>{Number(proof.calls || 0)} llamada(s) · {Number(proof.conversations || 0)} conversación(es) · {Number(proof.operations || 0)} operación(es)</p>
          {(proof.facts || []).length > 0 && <p><b>Hechos:</b> {(proof.facts || []).join(" · ")}</p>}
          {(proof.followUps || []).length > 0 && <p><b>Revisión:</b> {(proof.followUps || []).join(" · ")}</p>}
        </div>
      )}
    </>
  );
}
