import {
  Bot,
  Building2,
  CalendarClock,
  Check,
  ChevronRight,
  CircleAlert,
  CloudUpload,
  Link2,
  MessagesSquare,
  Phone,
  Save,
  ShieldCheck,
  Sparkles,
  UsersRound,
  Wrench
} from "lucide-react";
import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "../../api/client";
import { AppShell } from "../../components/AppShell/AppShell";
import {
  type AiAgentInput,
  type BusinessHour,
  type BusinessProfileInput,
  type KnowledgeItem,
  type ServiceItem
} from "../../features/settings/api";
import { useSettingsWorkspace } from "../../features/settings/useSettingsWorkspace";
import { editedSections, persistSettingsSection, sectionChanged, validateSection } from "../../features/settings/sectionSave";
import type {
  SavePhase,
  SectionKey,
  SettingsDraft
} from "../../features/settings/viewModel";
import { SettingsPanelView } from "./SettingsPanelView";
import { SettingsExpressIntro } from "./SettingsExpressIntro";
import styles from "./SettingsPage.module.css";

const sections: Array<{
  key: SectionKey;
  label: string;
  icon: typeof Building2;
}> = [
  { key: "business", label: "Negocio", icon: Building2 },
  { key: "receptionist", label: "Recepcionista IA", icon: Bot },
  { key: "services", label: "Servicios", icon: Wrench },
  { key: "hours", label: "Horarios", icon: CalendarClock },
  { key: "knowledge", label: "Conocimiento", icon: MessagesSquare },
  { key: "channels", label: "Canales", icon: Phone },
  { key: "integrations", label: "Integraciones", icon: Link2 },
  { key: "team", label: "Equipo", icon: UsersRound }
];

function sectionFromLocation(): SectionKey {
  const requested = new URLSearchParams(window.location.search).get("section")?.toLowerCase();
  if (requested === "responses") return "knowledge";
  if (requested && sections.some(section => section.key === requested)) {
    return requested as SectionKey;
  }
  return "business";
}

function defaultProfile(): BusinessProfileInput {
  return {
    presetKey: null,
    publicDescription: null,
    publicPhone: null,
    publicEmail: null,
    websiteUrl: null,
    addressLine: null,
    commune: null,
    city: null,
    region: null,
    countryCode: "CL",
    defaultCurrency: "CLP",
    sellsProducts: null,
    sellsServices: null,
    usesReservations: null
  };
}

function saveErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 400) {
      return error.message && error.message !== "HTTP 400"
        ? error.message
        : "Revisa los datos ingresados antes de guardar.";
    }
    if (error.status === 403) {
      return error.message && error.message !== "HTTP 403"
        ? error.message
        : "No tienes permisos para modificar esta configuración.";
    }
    if (error.status === 409) {
      return error.message && error.message !== "HTTP 409"
        ? error.message
        : "La configuración cambió en otra sesión. Revisa el conflicto e intenta nuevamente.";
    }
    if (error.status >= 500) {
      return error.message && error.message !== `HTTP ${error.status}`
        ? error.message
        : "No pudimos guardar la configuración. Intenta nuevamente.";
    }
  }
  return error instanceof Error && error.message
    ? error.message
    : "No pudimos guardar la configuración. Intenta nuevamente.";
}

export function SettingsPage() {
  const queryClient = useQueryClient();
  const model = useSettingsWorkspace();
  const [activeSection, setActiveSection] = useState<SectionKey>(sectionFromLocation);
  const [draft, setDraft] = useState<SettingsDraft | null>(null);
  const [savePhase, setSavePhase] = useState<SavePhase>("clean");
  const [saveError, setSaveError] = useState("");
  const hydrated = useRef(false);
  const baselineRef = useRef<SettingsDraft | null>(null);
  const submitLock = useRef(false);

  const coreSettled = !model.profile.isPending
    && !model.services.isPending
    && !model.hours.isPending
    && !model.knowledge.isPending
    && !model.agent.isPending;

  useEffect(() => {
    if (hydrated.current || !model.business.data || !coreSettled) return;

    const business = model.business.data;
    const profile = model.profile.data;
    const agent = model.agent.data;

    const initial: SettingsDraft = {
      businessName: String(business.name || ""),
      timezone: String(business.timezone || "America/Santiago"),
      language: String(business.language || "es"),
      humanTransferPhone: String(business.humanTransferPhone || ""),
      profile: {
        ...defaultProfile(),
        presetKey: profile?.presetKey ?? null,
        publicDescription: profile?.publicDescription ?? null,
        publicPhone: profile?.publicPhone ?? null,
        publicEmail: profile?.publicEmail ?? null,
        websiteUrl: profile?.websiteUrl ?? null,
        addressLine: profile?.addressLine ?? null,
        commune: profile?.commune ?? null,
        city: profile?.city ?? null,
        region: profile?.region ?? null,
        countryCode: profile?.countryCode ?? "CL",
        defaultCurrency: profile?.defaultCurrency ?? "CLP",
        sellsProducts: profile?.sellsProducts ?? null,
        sellsServices: profile?.sellsServices ?? null,
        usesReservations: profile?.usesReservations ?? null
      },
      services: (model.services.data ?? []).filter(service => service.active !== false).map(service => ({ ...service })),
      hours: (model.hours.data ?? []).map(hour => ({ ...hour })),
      knowledge: (model.knowledge.data ?? []).filter(item => item.active !== false).map(item => ({ ...item })),
      agent: {
        name: String(agent?.name || "Helvoca"),
        language: String(agent?.language || business.language || "es"),
        voice: String(agent?.voice || ""),
        greeting: String(agent?.greeting || ""),
        instructions: String(agent?.instructions || ""),
        active: agent?.active !== false,
        capabilities: [...(agent?.capabilities ?? [])]
      }
    };
    setDraft(initial);
    baselineRef.current = structuredClone(initial);

    hydrated.current = true;
    setSavePhase("clean");
  }, [
    coreSettled,
    model.agent.data,
    model.business.data,
    model.hours.data,
    model.knowledge.data,
    model.profile.data,
    model.services.data
  ]);

  const activeIsEditable = editedSections.includes(activeSection);
  const activeDirty = Boolean(draft && baselineRef.current
    && sectionChanged(activeSection, draft, baselineRef.current));
  const pendingOther = draft && baselineRef.current
    ? editedSections.filter(section => section !== activeSection
      && sectionChanged(section, draft, baselineRef.current!)).length
    : 0;
  const visibleSavePhase: SavePhase = savePhase === "saving" ? "saving"
    : activeDirty ? "dirty" : savePhase === "saved" ? "saved" : "clean";

  const readiness = useMemo(() => {
    const status = model.onboarding.data;
    if (!status) return "Estado parcial";
    if (status.readyForCalls) return "Listo para atender";
    return "Configuración pendiente";
  }, [model.onboarding.data]);

  function markDirty() {
    setSaveError("");
    setSavePhase("dirty");
  }

  function openSection(section: SectionKey) {
    setActiveSection(section);
    const url = new URL(window.location.href);
    url.searchParams.set("section", section);
    window.history.replaceState(null, "", url);
  }

  function updateRoot(
    key: "businessName" | "timezone" | "language" | "humanTransferPhone",
    value: string
  ) {
    setDraft(current => current ? { ...current, [key]: value } : current);
    markDirty();
  }

  function updateProfile(key: keyof BusinessProfileInput, value: string | boolean | null) {
    setDraft(current => current
      ? { ...current, profile: { ...current.profile, [key]: value } }
      : current);
    markDirty();
  }

  function updateAgent(key: keyof AiAgentInput, value: string | boolean | string[]) {
    setDraft(current => current
      ? { ...current, agent: { ...current.agent, [key]: value } }
      : current);
    markDirty();
  }

  function updateService(index: number, patch: Partial<ServiceItem>) {
    setDraft(current => {
      if (!current) return current;
      return {
        ...current,
        services: current.services.map((service, currentIndex) =>
          currentIndex === index ? { ...service, ...patch } : service)
      };
    });
    markDirty();
  }

  function addService() {
    setDraft(current => current
      ? {
        ...current,
        services: [
          ...current.services,
          { id: null, name: "", description: "", durationMinutes: 30, price: null, active: true }
        ]
      }
      : current);
    markDirty();
  }

  function removeService(index: number) {
    setDraft(current => current
      ? { ...current, services: current.services.filter((_, currentIndex) => currentIndex !== index) }
      : current);
    markDirty();
  }

  function updateHour(index: number, patch: Partial<BusinessHour>) {
    setDraft(current => {
      if (!current) return current;
      return {
        ...current,
        hours: current.hours.map((hour, currentIndex) =>
          currentIndex === index ? { ...hour, ...patch } : hour)
      };
    });
    markDirty();
  }

  function addHour() {
    setDraft(current => current
      ? {
        ...current,
        hours: [...current.hours, { dayOfWeek: 1, openTime: "09:00", closeTime: "18:00" }]
      }
      : current);
    markDirty();
  }

  function removeHour(index: number) {
    setDraft(current => current
      ? { ...current, hours: current.hours.filter((_, currentIndex) => currentIndex !== index) }
      : current);
    markDirty();
  }

  function updateKnowledge(index: number, patch: Partial<KnowledgeItem>) {
    setDraft(current => {
      if (!current) return current;
      return {
        ...current,
        knowledge: current.knowledge.map((item, currentIndex) =>
          currentIndex === index ? { ...item, ...patch } : item)
      };
    });
    markDirty();
  }

  function addKnowledge() {
    setDraft(current => current
      ? {
        ...current,
        knowledge: [
          ...current.knowledge,
          { id: null, title: "", category: "", content: "", active: true }
        ]
      }
      : current);
    markDirty();
  }

  function removeKnowledge(index: number) {
    setDraft(current => current
      ? { ...current, knowledge: current.knowledge.filter((_, currentIndex) => currentIndex !== index) }
      : current);
    markDirty();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draft || !baselineRef.current || !model.canManage || submitLock.current
      || !editedSections.includes(activeSection)) return;

    setSaveError("");
    if (!sectionChanged(activeSection, draft, baselineRef.current)) {
      setSavePhase("saved");
      return;
    }

    const failedSource = activeSection === "business" ? model.profile.isError
      : activeSection === "receptionist" ? model.agent.isError
      : activeSection === "services" ? model.services.isError
      : activeSection === "hours" ? model.hours.isError
      : model.knowledge.isError;
    if (failedSource) {
      setSaveError("No pudimos cargar esta sección. Recarga antes de guardar para proteger tus datos.");
      return;
    }
    const error = validateSection(activeSection, draft);
    if (error) {
      setSaveError(error);
      return;
    }

    const button = event.currentTarget.querySelector<HTMLButtonElement>('button[type="submit"]');
    submitLock.current = true;
    if (button) button.disabled = true;
    setSavePhase("saving");

    try {
      await persistSettingsSection(activeSection, draft, () => baselineRef.current!, result => {
        if (baselineRef.current) baselineRef.current = { ...baselineRef.current, ...result.patch };
        if (result.created) {
          const { section, index, item } = result.created;
          if (section === "services") {
            setDraft(current => current
              ? { ...current, services: current.services.map((value, i) => i === index
                ? { ...value, ...item as ServiceItem } : value) } : current);
          } else {
            setDraft(current => current
              ? { ...current, knowledge: current.knowledge.map((value, i) => i === index
                ? { ...value, ...item as KnowledgeItem } : value) } : current);
          }
        }
      });
      setSavePhase("saved");
    } catch (failure) {
      setSaveError(saveErrorMessage(failure));
      setSavePhase("dirty");
    } finally {
      submitLock.current = false;
      if (button) button.disabled = false;
      void queryClient.invalidateQueries({ queryKey: ["settings"] });
    }
  }

  if (model.primaryError) {
    return (
      <AppShell>
        <main className="rv-page-frame">
          <header className="rv-page-header">
            <div>
              <p className="eyebrow">TU NEGOCIO</p>
              <h1>Configuración</h1>
            </div>
          </header>
          <section className={styles.errorCard} role="alert">
            <CircleAlert size={20} aria-hidden="true" />
            <div>
              <strong>No pudimos cargar la configuración principal.</strong>
              <p>Tu información no fue modificada. Recarga la página para volver a intentarlo.</p>
            </div>
          </section>
        </main>
      </AppShell>
    );
  }

  if (model.primaryLoading || !draft) {
    return (
      <AppShell>
        <main className="rv-page-frame">
          <header className="rv-page-header">
            <div>
              <p className="eyebrow">TU NEGOCIO</p>
              <h1>Configuración</h1>
              <p>Preparando la configuración segura de tu negocio…</p>
            </div>
          </header>
          <div className={styles.loadingCard} role="status" aria-live="polite">
            <span className={styles.spinner} aria-hidden="true" />
            <span>Cargando configuración…</span>
          </div>
        </main>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <main className={`rv-page-frame ${styles.page}`} data-visual-page="settings">
        <header className={`rv-page-header ${styles.pageHeader}`}>
          <div>
            <p className="eyebrow">TU NEGOCIO</p>
            <h1>Configuración</h1>
            <p>Gestiona tu negocio y la recepción con IA desde una sola superficie.</p>
          </div>
          <div className={styles.headerMeta}>
            <span className={styles.readiness}>
              <ShieldCheck size={15} aria-hidden="true" />
              {readiness}
            </span>
            <a className={`button secondary ${styles.headerAction}`} href="/app/settings/import">
              <CloudUpload size={16} aria-hidden="true" />
              Importar o actualizar
            </a>
          </div>
        </header>

        <SettingsExpressIntro
          businessName={draft.businessName}
          status={model.onboarding.data}
          serviceCount={draft.services.length}
          knowledgeCount={draft.knowledge.length}
          canImport={model.canManage}
          onAdvanced={() => {
            document.getElementById("settingsAdvanced")?.scrollIntoView({
              behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth",
              block: "start"
            });
          }}
        />

        <section className={styles.hero} aria-label="Identidad de RecepVoz">
          <div className={styles.heroGlow} aria-hidden="true" />
          <div className={styles.heroCopy}>
            <span className={styles.heroKicker}>CENTRO DE CONTROL IA</span>
            <div>
              <strong>Configura cómo RecepVoz <span>representa a tu negocio</span></strong>
              <p>
                Importa información, define horarios, servicios, conocimiento y comportamiento.
                Cada cambio queda conectado a una recepción que sigue trabajando.
              </p>
              <div className={styles.heroSignals}>
                <span><i data-tone="green" />{readiness}</span>
                <span><i data-tone="cyan" />{draft.services.length} servicios</span>
                <span><i data-tone="violet" />{draft.knowledge.length} fuentes de conocimiento</span>
              </div>
              <a className={styles.heroAction} href="/app/settings/import">
                <CloudUpload size={16} aria-hidden="true" />
                Importar información
                <ChevronRight size={16} aria-hidden="true" />
              </a>
            </div>
          </div>
          <div className={styles.heroArt} aria-hidden="true">
            <span className={styles.heroOrbit} />
            <img className={styles.heroRobot} src="/app/assets/recepvoz/v2/settings/hero-ai-settings.webp" alt="" />
            <img className={styles.heroDocument} src="/app/assets/recepvoz/v2/settings/hero-document-organizer.webp" alt="" />
            <img className={styles.heroBusiness} src="/app/assets/recepvoz/v2/settings/business-storefront.webp" alt="" />
            <img className={styles.heroSources} src="/app/assets/recepvoz/v2/settings/import-sources.webp" alt="" />
          </div>
        </section>

        {model.partialErrors.length > 0 && (
          <div className={styles.partialAlert} role="alert">
            <CircleAlert size={18} aria-hidden="true" />
            <span>
              No pudimos cargar parte de la configuración ({model.partialErrors.join(", ")}).
              El resto permanece disponible y no se ha modificado.
            </span>
          </div>
        )}

        {saveError && (
          <div className={styles.saveAlert} role="alert">
            <CircleAlert size={18} aria-hidden="true" />
            <span>{saveError}</span>
          </div>
        )}

        <div className={styles.advancedHeading} id="settingsAdvanced">
          <div>
            <p className="eyebrow">AJUSTES DETALLADOS</p>
            <h2>Editar manualmente</h2>
            <p>Solo si lo necesitas. Tus datos existentes y los cambios pendientes se conservan.</p>
          </div>
          <span>{pendingOther > 0 ? `${pendingOther} sección(es) con cambios pendientes` : "Configuración avanzada"}</span>
        </div>
        <form className={styles.settingsSurface} onSubmit={handleSubmit} noValidate>
          <div
            className={styles.tabs}
            role="tablist"
            aria-label="Secciones de configuración"
          >
            {sections.map(({ key, label, icon: Icon }) => (
              <button
                key={key}
                className={activeSection === key ? styles.tabActive : styles.tab}
                type="button"
                role="tab"
                aria-selected={activeSection === key}
                aria-controls={`settings-panel-${key}`}
                disabled={savePhase === "saving"}
                onClick={() => openSection(key)}
              >
                <Icon size={16} aria-hidden="true" />
                <span>{label}</span>
              </button>
            ))}
          </div>

          <div className={styles.panelWrap}>
            <SettingsPanelView
              activeSection={activeSection}
              draft={draft}
              canManage={model.canManage && savePhase !== "saving"}
              canManageTeam={model.canManageTeam}
              canReadScheduleExceptions={model.canReadScheduleExceptions}
              canManageScheduleExceptions={model.canManageScheduleExceptions}
              canReadChannels={model.canReadChannels}
              canManageChannels={model.canManageChannels}
              canReadIntegrations={model.canReadIntegrations}
              canManageIntegrations={model.canManageIntegrations}
              voices={model.voices.data ?? []}
              phones={model.phones.data ?? []}
              updateRoot={updateRoot}
              updateProfile={updateProfile}
              updateAgent={updateAgent}
              updateService={updateService}
              addService={addService}
              removeService={removeService}
              updateHour={updateHour}
              addHour={addHour}
              removeHour={removeHour}
              updateKnowledge={updateKnowledge}
              addKnowledge={addKnowledge}
              removeKnowledge={removeKnowledge}
            />
          </div>

          {model.canManage && activeIsEditable && (
            <footer className={styles.saveBar}>
              <div
                className={
                  visibleSavePhase === "dirty"
                    ? styles.saveStatusDirty
                    : visibleSavePhase === "saving"
                      ? styles.saveStatusSaving
                      : styles.saveStatus
                }
                role="status"
                aria-live="polite"
              >
                {visibleSavePhase === "dirty" && <CircleAlert size={15} aria-hidden="true" />}
                {visibleSavePhase === "saving" && <span className={styles.miniSpinner} aria-hidden="true" />}
                {(visibleSavePhase === "clean" || visibleSavePhase === "saved") && <Check size={15} aria-hidden="true" />}
                {visibleSavePhase === "dirty" && "Cambios sin guardar"}
                {visibleSavePhase === "saving" && "Guardando…"}
                {visibleSavePhase === "saved" && "Guardado"}
                {visibleSavePhase === "clean" && "Guardado"}
                {pendingOther > 0 && visibleSavePhase !== "saving" && ` · ${pendingOther} otra(s) sección(es) pendiente(s)`}
              </div>

              <button
                className="button primary"
                type="submit"
                disabled={visibleSavePhase === "saving" || !activeDirty}
              >
                <Save size={16} aria-hidden="true" />
                {visibleSavePhase === "saving" ? "Guardando…" : "Guardar cambios"}
              </button>
            </footer>
          )}
        </form>
      </main>
    </AppShell>
  );
}
