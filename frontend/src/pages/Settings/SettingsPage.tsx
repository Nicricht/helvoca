import {
  Bot,
  Building2,
  CalendarClock,
  Check,
  ChevronRight,
  CircleAlert,
  CloudUpload,
  Globe2,
  Link2,
  MessagesSquare,
  Phone,
  Plus,
  Save,
  ShieldCheck,
  Sparkles,
  Trash2,
  UsersRound,
  Wrench
} from "lucide-react";
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type FormEvent,
  type ReactNode
} from "react";
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
import styles from "./SettingsPage.module.css";

type SectionKey =
  | "business"
  | "receptionist"
  | "services"
  | "hours"
  | "knowledge"
  | "channels"
  | "integrations"
  | "team";

type SavePhase = "clean" | "dirty" | "saving" | "saved";

interface DraftState {
  businessName: string;
  timezone: string;
  language: string;
  humanTransferPhone: string;
  profile: BusinessProfileInput;
  services: ServiceItem[];
  hours: BusinessHour[];
  knowledge: KnowledgeItem[];
  agent: AiAgentInput;
}

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

const dayNames = [
  "Domingo",
  "Lunes",
  "Martes",
  "Miércoles",
  "Jueves",
  "Viernes",
  "Sábado"
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

function Field({
  label,
  children,
  wide = false
}: {
  label: string;
  children: ReactNode;
  wide?: boolean;
}) {
  return (
    <label className={wide ? `${styles.field} ${styles.fieldWide}` : styles.field}>
      <span>{label}</span>
      {children}
    </label>
  );
}

export function SettingsPage() {
  const model = useSettingsWorkspace();
  const [activeSection, setActiveSection] = useState<SectionKey>(sectionFromLocation);
  const [draft, setDraft] = useState<DraftState | null>(null);
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

  function updateRoot<K extends keyof Pick<DraftState, "businessName" | "timezone" | "language" | "humanTransferPhone">>(
    key: K,
    value: DraftState[K]
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
      const services = current.services.map((service, currentIndex) =>
        currentIndex === index ? { ...service, ...patch } : service);
      return { ...current, services };
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
      const hours = current.hours.map((hour, currentIndex) =>
        currentIndex === index ? { ...hour, ...patch } : hour);
      return { ...current, hours };
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
      const knowledge = current.knowledge.map((item, currentIndex) =>
        currentIndex === index ? { ...item, ...patch } : item);
      return { ...current, knowledge };
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

    submitLock.current = true;
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
      <main className={`rv-page-frame ${styles.page}`}>
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
            <a className="button secondary" href="/business-import.html">
              <CloudUpload size={16} aria-hidden="true" />
              Importar o actualizar
            </a>
          </div>
        </header>

        <section className={styles.hero} aria-label="Configuración asistida">
          <div className={styles.heroGlow} aria-hidden="true" />
          <div className={styles.heroCopy}>
            <span className={styles.heroIcon}><Sparkles size={20} aria-hidden="true" /></span>
            <div>
              <strong>Configura tu negocio sin pelearte con formularios gigantes.</strong>
              <p>
                Puedes importar lo que ya tienes y luego revisar cada dato antes de convertirlo
                en información autoritativa del negocio.
              </p>
            </div>
          </div>
          <a className={styles.heroAction} href="/business-import.html">
            Revisar importación
            <ChevronRight size={16} aria-hidden="true" />
          </a>
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
            {activeSection === "business" && (
              <section
                id="settings-panel-business"
                className={styles.panel}
                role="tabpanel"
                aria-label="Negocio"
              >
                <div className={styles.sectionHeading}>
                  <div>
                    <span className={styles.sectionIcon}><Building2 size={19} aria-hidden="true" /></span>
                    <div>
                      <h2>Información del negocio</h2>
                      <p>Datos públicos y operativos que usa la recepcionista para responder correctamente.</p>
                    </div>
                  </div>
                </div>

                <div className={styles.formGrid}>
                  <Field label="Nombre del negocio" wide>
                    <input
                      value={draft.businessName}
                      readOnly={!model.canManage}
                      onChange={event => updateRoot("businessName", event.target.value)}
                    />
                  </Field>

                  <Field label="Descripción pública" wide>
                    <textarea
                      value={String(draft.profile.publicDescription ?? "")}
                      readOnly={!model.canManage}
                      onChange={event => updateProfile("publicDescription", event.target.value)}
                    />
                  </Field>

                  <Field label="Sitio web">
                    <input
                      type="url"
                      value={String(draft.profile.websiteUrl ?? "")}
                      readOnly={!model.canManage}
                      onChange={event => updateProfile("websiteUrl", event.target.value)}
                    />
                  </Field>

                  <Field label="Teléfono principal">
                    <input
                      value={String(draft.profile.publicPhone ?? "")}
                      readOnly={!model.canManage}
                      onChange={event => updateProfile("publicPhone", event.target.value)}
                    />
                  </Field>

                  <Field label="Correo público">
                    <input
                      type="email"
                      value={String(draft.profile.publicEmail ?? "")}
                      readOnly={!model.canManage}
                      onChange={event => updateProfile("publicEmail", event.target.value)}
                    />
                  </Field>

                  <Field label="Teléfono de transferencia">
                    <input
                      value={draft.humanTransferPhone}
                      readOnly={!model.canManage}
                      onChange={event => updateRoot("humanTransferPhone", event.target.value)}
                    />
                  </Field>

                  <Field label="Dirección" wide>
                    <input
                      value={String(draft.profile.addressLine ?? "")}
                      readOnly={!model.canManage}
                      onChange={event => updateProfile("addressLine", event.target.value)}
                    />
                  </Field>

                  <Field label="Comuna">
                    <input
                      value={String(draft.profile.commune ?? "")}
                      readOnly={!model.canManage}
                      onChange={event => updateProfile("commune", event.target.value)}
                    />
                  </Field>

                  <Field label="Ciudad">
                    <input
                      value={String(draft.profile.city ?? "")}
                      readOnly={!model.canManage}
                      onChange={event => updateProfile("city", event.target.value)}
                    />
                  </Field>

                  <Field label="Región">
                    <input
                      value={String(draft.profile.region ?? "")}
                      readOnly={!model.canManage}
                      onChange={event => updateProfile("region", event.target.value)}
                    />
                  </Field>

                  <Field label="País">
                    <input
                      maxLength={2}
                      value={String(draft.profile.countryCode ?? "")}
                      readOnly={!model.canManage}
                      onChange={event => updateProfile("countryCode", event.target.value)}
                    />
                  </Field>

                  <Field label="Zona horaria">
                    <input
                      value={draft.timezone}
                      readOnly={!model.canManage}
                      onChange={event => updateRoot("timezone", event.target.value)}
                    />
                  </Field>

                  <Field label="Idioma">
                    <input
                      value={draft.language}
                      readOnly={!model.canManage}
                      onChange={event => updateRoot("language", event.target.value)}
                    />
                  </Field>

                  <Field label="Moneda">
                    <input
                      maxLength={3}
                      value={String(draft.profile.defaultCurrency ?? "CLP")}
                      readOnly={!model.canManage}
                      onChange={event => updateProfile("defaultCurrency", event.target.value)}
                    />
                  </Field>
                </div>
              </section>
            )}

            {activeSection === "receptionist" && (
              <section
                id="settings-panel-receptionist"
                className={`${styles.panel} ${styles.aiPanel}`}
                role="tabpanel"
                aria-label="Recepcionista IA"
              >
                <div className={styles.sectionHeading}>
                  <div>
                    <span className={`${styles.sectionIcon} ${styles.aiIcon}`}><Bot size={19} aria-hidden="true" /></span>
                    <div>
                      <h2>Recepcionista IA</h2>
                      <p>Define cómo habla y se presenta. Las reglas comerciales siguen siendo autoridad del backend.</p>
                    </div>
                  </div>
                </div>

                <div className={styles.formGrid}>
                  <Field label="Nombre de la recepcionista">
                    <input
                      value={draft.agent.name}
                      readOnly={!model.canManage}
                      onChange={event => updateAgent("name", event.target.value)}
                    />
                  </Field>

                  <Field label="Idioma">
                    <input
                      value={draft.agent.language}
                      readOnly={!model.canManage}
                      onChange={event => updateAgent("language", event.target.value)}
                    />
                  </Field>

                  <Field label="Voz" wide>
                    <select
                      value={draft.agent.voice}
                      disabled={!model.canManage}
                      onChange={event => updateAgent("voice", event.target.value)}
                    >
                      {!model.voices.data?.some(option =>
                        (option.selection || option.code) === draft.agent.voice
                      ) && draft.agent.voice && (
                        <option value={draft.agent.voice}>{draft.agent.voice}</option>
                      )}
                      {(model.voices.data ?? []).map(option => {
                        const value = String(option.selection || option.code || "");
                        return <option key={value} value={value}>{option.name || value}</option>;
                      })}
                    </select>
                  </Field>

                  <Field label="Saludo inicial" wide>
                    <textarea
                      value={draft.agent.greeting}
                      readOnly={!model.canManage}
                      onChange={event => updateAgent("greeting", event.target.value)}
                    />
                  </Field>

                  <Field label="Instrucciones conversacionales" wide>
                    <textarea
                      className={styles.tallTextarea}
                      value={draft.agent.instructions}
                      readOnly={!model.canManage}
                      onChange={event => updateAgent("instructions", event.target.value)}
                    />
                  </Field>
                </div>

                <div className={styles.authorityNote}>
                  <ShieldCheck size={18} aria-hidden="true" />
                  <div>
                    <strong>La IA no decide precios, stock, disponibilidad ni pagos.</strong>
                    <span>Esos datos se consultan desde servicios autoritativos del backend.</span>
                  </div>
                </div>
              </section>
            )}

            {activeSection === "services" && (
              <section
                id="settings-panel-services"
                className={styles.panel}
                role="tabpanel"
                aria-label="Servicios"
              >
                <div className={styles.sectionHeading}>
                  <div>
                    <span className={styles.sectionIcon}><Wrench size={19} aria-hidden="true" /></span>
                    <div>
                      <h2>Servicios</h2>
                      <p>Lo que tus clientes pueden consultar y reservar.</p>
                    </div>
                  </div>
                  {model.canManage && (
                    <button className="button secondary" type="button" onClick={addService}>
                      <Plus size={15} aria-hidden="true" />
                      Añadir servicio
                    </button>
                  )}
                </div>

                <div className={styles.cardList}>
                  {draft.services.map((service, index) => (
                    <article className={styles.editCard} key={service.id || `new-service-${index}`}>
                      <strong className={styles.cardTitle}>{service.name || "Servicio nuevo"}</strong>
                      <div className={styles.editCardGrid}>
                        <Field label="Nombre">
                          <input
                            value={service.name}
                            readOnly={!model.canManage}
                            onChange={event => updateService(index, { name: event.target.value })}
                          />
                        </Field>
                        <Field label="Duración (min)">
                          <input
                            type="number"
                            min="1"
                            value={service.durationMinutes}
                            readOnly={!model.canManage}
                            onChange={event => updateService(index, {
                              durationMinutes: Number(event.target.value)
                            })}
                          />
                        </Field>
                        <Field label="Precio">
                          <input
                            type="number"
                            min="0"
                            value={service.price ?? ""}
                            readOnly={!model.canManage}
                            onChange={event => updateService(index, {
                              price: event.target.value === "" ? null : Number(event.target.value)
                            })}
                          />
                        </Field>
                        <Field label="Descripción" wide>
                          <input
                            value={String(service.description ?? "")}
                            readOnly={!model.canManage}
                            onChange={event => updateService(index, { description: event.target.value })}
                          />
                        </Field>
                      </div>
                      {model.canManage && (
                        <button
                          className={styles.iconDanger}
                          type="button"
                          aria-label={`Eliminar servicio ${service.name || index + 1}`}
                          onClick={() => removeService(index)}
                        >
                          <Trash2 size={16} aria-hidden="true" />
                        </button>
                      )}
                    </article>
                  ))}
                </div>
              </section>
            )}

            {activeSection === "hours" && (
              <section
                id="settings-panel-hours"
                className={styles.panel}
                role="tabpanel"
                aria-label="Horarios"
              >
                <div className={styles.sectionHeading}>
                  <div>
                    <span className={styles.sectionIcon}><CalendarClock size={19} aria-hidden="true" /></span>
                    <div>
                      <h2>Horarios de atención</h2>
                      <p>Franjas semanales utilizadas por disponibilidad y reservas.</p>
                    </div>
                  </div>
                  {model.canManage && (
                    <button className="button secondary" type="button" onClick={addHour}>
                      <Plus size={15} aria-hidden="true" />
                      Añadir horario
                    </button>
                  )}
                </div>

                <div className={styles.hoursList}>
                  {draft.hours.map((hour, index) => (
                    <div className={styles.hourRow} key={`${hour.dayOfWeek}-${index}`}>
                      <select
                        aria-label={`Día ${index + 1}`}
                        value={hour.dayOfWeek}
                        disabled={!model.canManage}
                        onChange={event => updateHour(index, { dayOfWeek: Number(event.target.value) })}
                      >
                        {dayNames.map((name, day) => <option value={day} key={name}>{name}</option>)}
                      </select>
                      <input
                        aria-label={`Apertura ${index + 1}`}
                        type="time"
                        value={String(hour.openTime).slice(0, 5)}
                        readOnly={!model.canManage}
                        onChange={event => updateHour(index, { openTime: event.target.value })}
                      />
                      <span aria-hidden="true">→</span>
                      <input
                        aria-label={`Cierre ${index + 1}`}
                        type="time"
                        value={String(hour.closeTime).slice(0, 5)}
                        readOnly={!model.canManage}
                        onChange={event => updateHour(index, { closeTime: event.target.value })}
                      />
                      {model.canManage && (
                        <button
                          className={styles.iconDanger}
                          type="button"
                          aria-label={`Eliminar horario ${index + 1}`}
                          onClick={() => removeHour(index)}
                        >
                          <Trash2 size={16} aria-hidden="true" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </section>
            )}

            {activeSection === "knowledge" && (
              <section
                id="settings-panel-knowledge"
                className={styles.panel}
                role="tabpanel"
                aria-label="Conocimiento"
              >
                <div className={styles.sectionHeading}>
                  <div>
                    <span className={styles.sectionIcon}><MessagesSquare size={19} aria-hidden="true" /></span>
                    <div>
                      <h2>Conocimiento y respuestas</h2>
                      <p>Preguntas frecuentes e información descriptiva que la IA puede consultar.</p>
                    </div>
                  </div>
                  {model.canManage && (
                    <button className="button secondary" type="button" onClick={addKnowledge}>
                      <Plus size={15} aria-hidden="true" />
                      Añadir respuesta
                    </button>
                  )}
                </div>

                <div className={styles.cardList}>
                  {draft.knowledge.map((item, index) => (
                    <article className={styles.editCard} key={item.id || `new-knowledge-${index}`}>
                      <div className={styles.editCardGrid}>
                        <Field label="Título">
                          <input
                            value={item.title}
                            readOnly={!model.canManage}
                            onChange={event => updateKnowledge(index, { title: event.target.value })}
                          />
                        </Field>
                        <Field label="Categoría">
                          <input
                            value={String(item.category ?? "")}
                            readOnly={!model.canManage}
                            onChange={event => updateKnowledge(index, { category: event.target.value })}
                          />
                        </Field>
                        <Field label="Respuesta" wide>
                          <textarea
                            value={item.content}
                            readOnly={!model.canManage}
                            onChange={event => updateKnowledge(index, { content: event.target.value })}
                          />
                        </Field>
                      </div>
                      {model.canManage && (
                        <button
                          className={styles.iconDanger}
                          type="button"
                          aria-label={`Eliminar respuesta ${item.title || index + 1}`}
                          onClick={() => removeKnowledge(index)}
                        >
                          <Trash2 size={16} aria-hidden="true" />
                        </button>
                      )}
                    </article>
                  ))}
                </div>
              </section>
            )}

            {activeSection === "channels" && (
              <section
                id="settings-panel-channels"
                className={styles.panel}
                role="tabpanel"
                aria-label="Canales"
              >
                <div className={styles.sectionHeading}>
                  <div>
                    <span className={styles.sectionIcon}><Phone size={19} aria-hidden="true" /></span>
                    <div>
                      <h2>Canales</h2>
                      <p>Estado observable de tus números. Las acciones de proveedor se mantienen separadas y explícitas.</p>
                    </div>
                  </div>
                </div>

                <div className={styles.channelList}>
                  {(model.phones.data ?? []).length === 0 && (
                    <div className={styles.emptyCard}>
                      <Phone size={20} aria-hidden="true" />
                      <div>
                        <strong>No hay números conectados.</strong>
                        <span>Esta pantalla no aprovisiona ni activa proveedores automáticamente.</span>
                      </div>
                    </div>
                  )}
                  {(model.phones.data ?? []).map(phone => (
                    <article className={styles.channelCard} key={phone.id || phone.phoneNumber}>
                      <div>
                        <strong>{phone.phoneNumber || "Número"}</strong>
                        <span>{phone.provider || "Proveedor"} · {phone.active ? "Activo" : "Inactivo"}</span>
                      </div>
                      <span className={phone.active ? styles.goodPill : styles.mutedPill}>
                        {phone.whatsappEnabled ? "WhatsApp habilitado" : "Voz"}
                      </span>
                    </article>
                  ))}
                </div>
              </section>
            )}

            {activeSection === "integrations" && (
              <section
                id="settings-panel-integrations"
                className={styles.panel}
                role="tabpanel"
                aria-label="Integraciones"
              >
                <div className={styles.sectionHeading}>
                  <div>
                    <span className={styles.sectionIcon}><Globe2 size={19} aria-hidden="true" /></span>
                    <div>
                      <h2>Integraciones</h2>
                      <p>Los proveedores externos se administran con endpoints explícitos y nunca muestran credenciales completas.</p>
                    </div>
                  </div>
                </div>
                <div className={styles.authorityNote}>
                  <ShieldCheck size={18} aria-hidden="true" />
                  <div>
                    <strong>Zona segura de integraciones</strong>
                    <span>
                      Activación de WhatsApp, pagos y otros proveedores se migrará como bloque independiente
                      con confirmación, permisos y E2E mockeado.
                    </span>
                  </div>
                </div>
              </section>
            )}

            {activeSection === "team" && (
              <section
                id="settings-panel-team"
                className={styles.panel}
                role="tabpanel"
                aria-label="Equipo"
              >
                <div className={styles.sectionHeading}>
                  <div>
                    <span className={styles.sectionIcon}><UsersRound size={19} aria-hidden="true" /></span>
                    <div>
                      <h2>Equipo y permisos</h2>
                      <p>La gestión de invitaciones conserva por ahora el flujo legacy hasta certificar su bloque React.</p>
                    </div>
                  </div>
                </div>
                <a className="button secondary" href="/settings.html?section=team">
                  Abrir gestión de equipo actual
                  <ChevronRight size={15} aria-hidden="true" />
                </a>
              </section>
            )}
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
