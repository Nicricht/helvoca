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
import { ApiError } from "../../api/client";
import { AppShell } from "../../components/AppShell/AppShell";
import {
  saveAiAgent,
  saveBusinessProfile,
  saveSetup,
  type AiAgentInput,
  type BusinessHour,
  type BusinessProfileInput,
  type KnowledgeItem,
  type ServiceItem
} from "../../features/settings/api";
import { useSettingsWorkspace } from "../../features/settings/useSettingsWorkspace";
import type {
  SavePhase,
  SectionKey,
  SettingsDraft
} from "../../features/settings/viewModel";
import { SettingsPanelView } from "./SettingsPanelView";
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

function nullable(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
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
  const model = useSettingsWorkspace();
  const [activeSection, setActiveSection] = useState<SectionKey>(sectionFromLocation);
  const [draft, setDraft] = useState<SettingsDraft | null>(null);
  const [savePhase, setSavePhase] = useState<SavePhase>("clean");
  const [saveError, setSaveError] = useState("");
  const hydrated = useRef(false);
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

    setDraft({
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
      services: (model.services.data ?? []).map(service => ({ ...service })),
      hours: (model.hours.data ?? []).map(hour => ({ ...hour })),
      knowledge: (model.knowledge.data ?? []).map(item => ({ ...item })),
      agent: {
        name: String(agent?.name || "Helvoca"),
        language: String(agent?.language || business.language || "es"),
        voice: String(agent?.voice || ""),
        greeting: String(agent?.greeting || ""),
        instructions: String(agent?.instructions || ""),
        active: agent?.active !== false,
        capabilities: [...(agent?.capabilities ?? [])]
      }
    });

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
    if (!draft || !model.canManage || submitLock.current) return;

    setSaveError("");

    if (!draft.businessName.trim()) {
      setSaveError("El nombre del negocio es obligatorio.");
      openSection("business");
      return;
    }

    const validServices = draft.services.filter(service => service.name.trim());
    if (!validServices.length) {
      setSaveError("Añade al menos un servicio antes de guardar.");
      openSection("services");
      return;
    }

    if (!draft.hours.length) {
      setSaveError("Configura al menos un horario de atención.");
      openSection("hours");
      return;
    }

    if (!draft.agent.greeting.trim()) {
      setSaveError("Define el saludo inicial de la recepcionista IA.");
      openSection("receptionist");
      return;
    }

    const submitButton = event.currentTarget.querySelector<HTMLButtonElement>('button[type="submit"]');
    submitLock.current = true;
    if (submitButton) submitButton.disabled = true;
    setSavePhase("saving");

    try {
      await saveSetup({
        businessName: draft.businessName.trim(),
        timezone: draft.timezone.trim(),
        language: draft.language.trim(),
        humanTransferPhone: nullable(draft.humanTransferPhone),
        services: validServices.map(service => ({
          id: service.id ?? null,
          name: service.name.trim(),
          description: nullable(String(service.description ?? "")),
          durationMinutes: Number(service.durationMinutes) || 30,
          price: service.price === null || service.price === undefined
            ? null
            : Number(service.price)
        })),
        hours: draft.hours.map(hour => ({
          dayOfWeek: Number(hour.dayOfWeek),
          openTime: String(hour.openTime).slice(0, 5),
          closeTime: String(hour.closeTime).slice(0, 5)
        })),
        knowledge: draft.knowledge
          .filter(item => item.title.trim() && item.content.trim())
          .map(item => ({
            id: item.id ?? null,
            title: item.title.trim(),
            category: nullable(String(item.category ?? "")),
            content: item.content.trim()
          }))
      });

      await saveBusinessProfile({
        ...draft.profile,
        presetKey: nullable(String(draft.profile.presetKey ?? "")),
        publicDescription: nullable(String(draft.profile.publicDescription ?? "")),
        publicPhone: nullable(String(draft.profile.publicPhone ?? "")),
        publicEmail: nullable(String(draft.profile.publicEmail ?? "")),
        websiteUrl: nullable(String(draft.profile.websiteUrl ?? "")),
        addressLine: nullable(String(draft.profile.addressLine ?? "")),
        commune: nullable(String(draft.profile.commune ?? "")),
        city: nullable(String(draft.profile.city ?? "")),
        region: nullable(String(draft.profile.region ?? "")),
        countryCode: nullable(String(draft.profile.countryCode ?? "").toUpperCase()),
        defaultCurrency: String(draft.profile.defaultCurrency || "CLP").trim().toUpperCase()
      });

      await saveAiAgent({
        ...draft.agent,
        name: draft.agent.name.trim(),
        language: draft.agent.language.trim(),
        voice: draft.agent.voice.trim(),
        greeting: draft.agent.greeting.trim(),
        instructions: draft.agent.instructions.trim(),
        capabilities: [...draft.agent.capabilities]
      });

      setSavePhase("saved");
    } catch (error) {
      setSaveError(saveErrorMessage(error));
      setSavePhase("dirty");
    } finally {
      submitLock.current = false;
      if (submitButton) submitButton.disabled = false;
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

        <section className={styles.hero} aria-label="Configuración asistida">
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
              canManage={model.canManage}
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

          {model.canManage && (
            <footer className={styles.saveBar}>
              <div
                className={
                  savePhase === "dirty"
                    ? styles.saveStatusDirty
                    : savePhase === "saving"
                      ? styles.saveStatusSaving
                      : styles.saveStatus
                }
                role="status"
                aria-live="polite"
              >
                {savePhase === "dirty" && <CircleAlert size={15} aria-hidden="true" />}
                {savePhase === "saving" && <span className={styles.miniSpinner} aria-hidden="true" />}
                {(savePhase === "clean" || savePhase === "saved") && <Check size={15} aria-hidden="true" />}
                {savePhase === "dirty" && "Cambios sin guardar"}
                {savePhase === "saving" && "Guardando…"}
                {savePhase === "saved" && "Guardado"}
                {savePhase === "clean" && "Guardado"}
              </div>

              <button
                className="button primary"
                type="submit"
                disabled={savePhase === "saving"}
              >
                <Save size={16} aria-hidden="true" />
                {savePhase === "saving" ? "Guardando…" : "Guardar cambios"}
              </button>
            </footer>
          )}
        </form>
      </main>
    </AppShell>
  );
}
