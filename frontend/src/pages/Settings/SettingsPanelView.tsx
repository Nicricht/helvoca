import {
  BedDouble,
  Bot,
  Building2,
  CalendarClock,
  CarFront,
  Check,
  Dumbbell,
  Globe2,
  Hospital,
  House,
  MessagesSquare,
  PawPrint,
  Phone,
  Plus,
  Scale,
  Scissors,
  ShieldCheck,
  ShoppingBag,
  Stethoscope,
  Store,
  Trash2,
  UsersRound,
  Utensils,
  Wrench
} from "lucide-react";
import type { KeyboardEvent, ReactNode } from "react";
import type {
  AiAgentInput,
  BusinessHour,
  BusinessProfileInput,
  KnowledgeItem,
  PhoneNumber,
  ServiceItem,
  VoiceOption
} from "../../features/settings/api";
import type { SectionKey, SettingsDraft } from "../../features/settings/viewModel";
import { getSectorSetupGuide } from "../../features/settings/sectorSetupGuides";
import { ChannelsSettingsPanel } from "./ChannelsSettingsPanel";
import { IntegrationsSettingsPanel } from "./IntegrationsSettingsPanel";
import { ScheduleExceptionsPanel } from "./ScheduleExceptionsPanel";
import { TeamSettingsPanel } from "./TeamSettingsPanel";
import styles from "./SettingsPage.module.css";

const dayNames = [
  "Domingo",
  "Lunes",
  "Martes",
  "Miércoles",
  "Jueves",
  "Viernes",
  "Sábado"
];


const businessPresetOptions = [
  ["", "Otro negocio / general"],
  ["clinic", "Clínica / centro médico"],
  ["dental", "Dentista"],
  ["veterinary", "Veterinaria / mascotas"],
  ["salon", "Peluquería / barbería"],
  ["restaurant", "Restaurante / alimentos"],
  ["hardware_store", "Ferretería / materiales"],
  ["store", "Tienda / comercio"],
  ["workshop", "Taller mecánico / reparación"],
  ["legal", "Estudio jurídico"],
  ["real_estate", "Inmobiliaria"],
  ["gym", "Gimnasio"],
  ["hospitality", "Hotel / hospedaje"],
  ["services", "Servicios (perfil previo)"],
  ["professional", "Profesional (perfil previo)"]
] as const;

// Visual presentation uses existing presetKey values; it never enables an AI capability.
const businessPresetCards = [
  { value: "clinic", label: "Clínica", icon: Hospital },
  { value: "dental", label: "Dentista", icon: Stethoscope },
  { value: "veterinary", label: "Veterinaria", icon: PawPrint },
  { value: "salon", label: "Peluquería", icon: Scissors },
  { value: "restaurant", label: "Restaurante", icon: Utensils },
  { value: "hardware_store", label: "Ferretería", icon: Wrench },
  { value: "store", label: "Tienda", icon: ShoppingBag },
  { value: "workshop", label: "Taller mecánico", icon: CarFront },
  { value: "legal", label: "Estudio jurídico", icon: Scale },
  { value: "real_estate", label: "Inmobiliaria", icon: House },
  { value: "gym", label: "Gimnasio", icon: Dumbbell },
  { value: "hospitality", label: "Hotel", icon: BedDouble },
  { value: "", label: "Otro negocio", icon: Store }
] as const;

const businessPresetSuggestions: Record<string, string> = {
  store: "Revisa productos, precios, disponibilidad, horarios, entregas y políticas de cambios. No confirmes stock sin verificar.",
  hardware_store: "Prioriza catálogo, stock, cotizaciones, pedidos y despacho. Revisa medidas, cantidades y disponibilidad antes de cerrar una venta.",
  services: "Revisa servicios, duraciones, tarifas, horarios, zona de cobertura y condiciones de reserva.",
  restaurant: "Revisa carta, precios, horarios, preguntas frecuentes, pedidos y reservas. No confirmes productos agotados como disponibles.",
  clinic: "Revisa especialidades, profesionales, duración de consultas, horarios, precios y reservas. No inventes disponibilidad ni indicaciones clínicas.",
  veterinary: "Revisa especies atendidas, servicios, profesionales, horarios, urgencias, precios y citas. No inventes disponibilidad ni recomendaciones veterinarias.",
  salon: "Revisa servicios, duraciones, precios, profesionales y disponibilidad para reservas.",
  workshop: "Revisa tipos de reparación, presupuestos, horarios, plazos y requisitos para recibir equipos.",
  hospitality: "Revisa tipos de alojamiento, tarifas, horarios de ingreso y salida y políticas de reserva.",
  professional: "Revisa servicios profesionales, tarifas, horarios, canales de contacto y modalidad de reserva.",
  dental: "Revisa tratamientos, profesionales, duraciones, precios de evaluación, disponibilidad y protocolo de urgencias. No diagnostiques.",
  legal: "Revisa áreas jurídicas, abogados, agenda, tarifas y derivación de urgencias. No entregues asesoría legal personalizada.",
  real_estate: "Revisa inmuebles publicados, estados, precios, visitas, requisitos y contactos. No inventes disponibilidad.",
  gym: "Revisa planes, horarios, clases, cupos, cuotas, cancelaciones y profesionales. No inventes beneficios ni cobros."
};

function focusAdjacentSectorCard(event: KeyboardEvent<HTMLButtonElement>) {
  const key = event.key;
  if (!["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Home", "End"].includes(key)) return;
  const cards = Array.from(
    event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>("[data-business-preset-card]:not(:disabled)") ?? []
  );
  if (!cards.length) return;
  event.preventDefault();
  const index = cards.indexOf(event.currentTarget);
  const next = key === "Home" ? 0
    : key === "End" ? cards.length - 1
    : (index + (key === "ArrowRight" || key === "ArrowDown" ? 1 : -1) + cards.length) % cards.length;
  cards[next]?.focus();
}

const agentCapabilities = [
  ["GET_BUSINESS_INFORMATION", "Información"],
  ["LIST_SERVICES", "Servicios"],
  ["SEARCH_KNOWLEDGE", "Conocimiento"],
  ["FIND_CALLER", "Identificar cliente"],
  ["REGISTER_CALLER", "Registrar cliente"],
  ["LIST_AVAILABLE_SLOTS", "Ver horarios"],
  ["CHECK_BOOKING_AVAILABILITY", "Comprobar horario"],
  ["CREATE_BOOKING", "Crear reserva"],
  ["LIST_CUSTOMER_BOOKINGS", "Ver reservas"],
  ["RESCHEDULE_BOOKING", "Reprogramar"],
  ["CANCEL_BOOKING", "Cancelar reserva"],
  ["CREATE_REQUEST", "Crear solicitud"],
  ["RECORD_UNANSWERED_QUESTION", "Guardar pregunta"],
  ["TRANSFER_TO_HUMAN", "Transferir a persona"]
] as const;

function profileBooleanValue(value: boolean | null | undefined) {
  if (value === true) return "true";
  if (value === false) return "false";
  return "";
}

function parseProfileBoolean(value: string): boolean | null {
  if (value === "true") return true;
  if (value === "false") return false;
  return null;
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

interface SettingsPanelViewProps {
  activeSection: SectionKey;
  draft: SettingsDraft;
  persistedDraft: SettingsDraft;
  canManage: boolean;
  canManageTeam: boolean;
  canReadScheduleExceptions: boolean;
  canManageScheduleExceptions: boolean;
  canReadChannels: boolean;
  canManageChannels: boolean;
  canReadIntegrations: boolean;
  canManageIntegrations: boolean;
  voices: VoiceOption[];
  phones: PhoneNumber[];
  updateRoot: (
    key: "businessName" | "timezone" | "language" | "humanTransferPhone",
    value: string
  ) => void;
  updateProfile: (key: keyof BusinessProfileInput, value: string | boolean | null) => void;
  navigateToSection: (section: SectionKey) => void;
  updateAgent: (key: keyof AiAgentInput, value: string | boolean | string[]) => void;
  updateService: (index: number, patch: Partial<ServiceItem>) => void;
  addService: () => void;
  removeService: (index: number) => void;
  updateHour: (index: number, patch: Partial<BusinessHour>) => void;
  addHour: () => void;
  removeHour: (index: number) => void;
  updateKnowledge: (index: number, patch: Partial<KnowledgeItem>) => void;
  addKnowledge: () => void;
  removeKnowledge: (index: number) => void;
}

export function SettingsPanelView(props: SettingsPanelViewProps) {
  const {
    activeSection,
    draft,
    persistedDraft,
    canManage,
    canManageTeam,
    canReadScheduleExceptions,
    canManageScheduleExceptions,
    canReadChannels,
    canManageChannels,
    canReadIntegrations,
    canManageIntegrations,
    voices,
    phones,
    updateRoot,
    updateProfile,
    navigateToSection,
    updateAgent,
    updateService,
    addService,
    removeService,
    updateHour,
    addHour,
    removeHour,
    updateKnowledge,
    addKnowledge,
    removeKnowledge
  } = props;

  if (activeSection === "business") {
    const guide = getSectorSetupGuide(draft.profile.presetKey);
    const sectorSaved = (draft.profile.presetKey ?? "") === (persistedDraft.profile.presetKey ?? "");
    const savedServices = persistedDraft.services.filter(
      item => item.id && item.active !== false && Boolean(item.name?.trim())
    ).length;
    const savedHours = persistedDraft.hours.filter(
      hour => String(hour.openTime).slice(0, 5) < String(hour.closeTime).slice(0, 5)
    ).length;
    const savedAnswers = persistedDraft.knowledge.filter(
      item => item.id && item.active !== false && Boolean(item.title?.trim()) && Boolean(item.content?.trim())
    ).length;
    return (
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
              readOnly={!canManage}
              onChange={event => updateRoot("businessName", event.target.value)}
            />
          </Field>

          <div className={`${styles.field} ${styles.fieldWide}`}>
            <span>Rubro</span>
            <p className={styles.businessPresetIntro}>Elige el tipo de negocio. Solo se guardará cuando confirmes los cambios.</p>
            <div className={styles.businessPresetGrid} role="group" aria-label="Tipos de negocio">
              {businessPresetCards.map(({ value, label, icon: Icon }) => {
                const selected = String(draft.profile.presetKey ?? "") === value;
                return (
                  <button
                    key={value || "other"}
                    type="button"
                    data-business-preset-card
                    data-selected={selected ? "true" : undefined}
                    className={styles.businessPresetCard}
                    aria-pressed={selected}
                    disabled={!canManage}
                    onClick={() => updateProfile("presetKey", value || null)}
                    onKeyDown={focusAdjacentSectorCard}
                  >
                    <span className={styles.businessPresetIcon}><Icon size={22} aria-hidden="true" /></span>
                    <span>{label}</span>
                    {selected && <Check className={styles.businessPresetCheck} size={16} aria-hidden="true" />}
                  </button>
                );
              })}
            </div>
            <label className={styles.businessPresetFallback}>
              <span>También puedes elegir desde la lista</span>
              <select
                aria-label="Rubro"
                value={String(draft.profile.presetKey ?? "")}
                disabled={!canManage}
                onChange={event => updateProfile("presetKey", event.target.value || null)}
              >
                {businessPresetOptions.map(([value, label]) => (
                  <option value={value} key={value || "general"}>{label}</option>
                ))}
              </select>
            </label>
          </div>

          <Field label="Moneda">
            <select
              value={String(draft.profile.defaultCurrency ?? "CLP")}
              disabled={!canManage}
              onChange={event => updateProfile("defaultCurrency", event.target.value)}
            >
              {["CLP", "USD", "EUR", "MXN", "COP", "PEN", "ARS", "BRL"].map(currency => (
                <option value={currency} key={currency}>{currency}</option>
              ))}
            </select>
          </Field>

          <Field label="Productos">
            <select
              value={profileBooleanValue(draft.profile.sellsProducts)}
              disabled={!canManage}
              onChange={event => updateProfile("sellsProducts", parseProfileBoolean(event.target.value))}
            >
              <option value="">Sin definir</option>
              <option value="true">Sí</option>
              <option value="false">No</option>
            </select>
          </Field>

          <Field label="Ofrece servicios">
            <select
              value={profileBooleanValue(draft.profile.sellsServices)}
              disabled={!canManage}
              onChange={event => updateProfile("sellsServices", parseProfileBoolean(event.target.value))}
            >
              <option value="">Sin definir</option>
              <option value="true">Sí</option>
              <option value="false">No</option>
            </select>
          </Field>

          <Field label="Reservas">
            <select
              value={profileBooleanValue(draft.profile.usesReservations)}
              disabled={!canManage}
              onChange={event => updateProfile("usesReservations", parseProfileBoolean(event.target.value))}
            >
              <option value="">Sin definir</option>
              <option value="true">Sí</option>
              <option value="false">No</option>
            </select>
          </Field>

          <Field label="Descripción pública" wide>
            <textarea
              value={String(draft.profile.publicDescription ?? "")}
              readOnly={!canManage}
              onChange={event => updateProfile("publicDescription", event.target.value)}
            />
          </Field>

          <Field label="Sitio web">
            <input
              type="url"
              value={String(draft.profile.websiteUrl ?? "")}
              readOnly={!canManage}
              onChange={event => updateProfile("websiteUrl", event.target.value)}
            />
          </Field>

          <Field label="Teléfono principal">
            <input
              value={String(draft.profile.publicPhone ?? "")}
              readOnly={!canManage}
              onChange={event => updateProfile("publicPhone", event.target.value)}
            />
          </Field>

          <Field label="Correo público">
            <input
              type="email"
              value={String(draft.profile.publicEmail ?? "")}
              readOnly={!canManage}
              onChange={event => updateProfile("publicEmail", event.target.value)}
            />
          </Field>

          <Field label="Teléfono de transferencia">
            <input
              value={draft.humanTransferPhone}
              readOnly={!canManage}
              onChange={event => updateRoot("humanTransferPhone", event.target.value)}
            />
          </Field>

          <Field label="Dirección" wide>
            <input
              value={String(draft.profile.addressLine ?? "")}
              readOnly={!canManage}
              onChange={event => updateProfile("addressLine", event.target.value)}
            />
          </Field>

          <Field label="Comuna">
            <input
              value={String(draft.profile.commune ?? "")}
              readOnly={!canManage}
              onChange={event => updateProfile("commune", event.target.value)}
            />
          </Field>

          <Field label="Ciudad">
            <input
              value={String(draft.profile.city ?? "")}
              readOnly={!canManage}
              onChange={event => updateProfile("city", event.target.value)}
            />
          </Field>

          <Field label="Región">
            <input
              value={String(draft.profile.region ?? "")}
              readOnly={!canManage}
              onChange={event => updateProfile("region", event.target.value)}
            />
          </Field>

          <Field label="País">
            <input
              maxLength={2}
              value={String(draft.profile.countryCode ?? "")}
              readOnly={!canManage}
              onChange={event => updateProfile("countryCode", event.target.value)}
            />
          </Field>

          <Field label="Zona horaria">
            <input
              value={draft.timezone}
              readOnly={!canManage}
              onChange={event => updateRoot("timezone", event.target.value)}
            />
          </Field>

          <Field label="Idioma">
            <input
              value={draft.language}
              readOnly={!canManage}
              onChange={event => updateRoot("language", event.target.value)}
            />
          </Field>

        </div>

        {draft.profile.presetKey && businessPresetSuggestions[draft.profile.presetKey] && (
          <div className={styles.businessHint} role="status">
            <strong>Sugerencia para este rubro</strong>
            <span>{businessPresetSuggestions[draft.profile.presetKey]}</span>
          </div>
        )}
        <section className={styles.sectorSetupGuide} role="region" aria-label="Preparación del negocio">
          <h3>Antes de atender llamadas: {guide.title}</h3>
          <p>Confirma estos datos con información real del negocio. La guía no crea servicios, precios, reservas ni activa canales.</p>
          <ol>
            {guide.questions.map(question => <li key={question}>{question}</li>)}
          </ol>
          <div className={styles.sectorSetupActions}>
            <button type="button" onClick={() => navigateToSection("services")}>Ir a Servicios</button>
            <button type="button" onClick={() => navigateToSection("hours")}>Ir a Horarios</button>
            <button type="button" onClick={() => navigateToSection("knowledge")}>Ir a Conocimiento</button>
          </div>
          <div className={styles.sectorMissingInformation}>
            <strong>Si falta información durante una llamada</strong>
            <p>{guide.missingInformation}</p>
          </div>
        </section>
        <section className={styles.sectorSavedEvidence} role="region" aria-label="Datos guardados para la atención">
          <h3>Datos registrados en este negocio</h3>
          <p>Estas cantidades reflejan datos guardados, no el formulario sin guardar.</p>
          <ul>
            <li>{sectorSaved ? "Rubro guardado" : "Rubro sin guardar"}</li>
            <li>{savedServices} {savedServices === 1 ? "servicio registrado" : "servicios registrados"}</li>
            <li>{savedHours} {savedHours === 1 ? "horario registrado" : "horarios registrados"}</li>
            <li>{savedAnswers} {savedAnswers === 1 ? "respuesta registrada" : "respuestas registradas"}</li>
          </ul>
          <p className={styles.sectorSavedWarning}>Estos registros no certifican llamadas reales, disponibilidad, inventario ni herramientas habilitadas. Verifica los datos y canales antes de operar.</p>
        </section>
      </section>
    );
  }

  if (activeSection === "receptionist") {
    return (
      <section
        id="settings-panel-receptionist"
        className={`${styles.panel} ${styles.aiPanel}`}
        role="tabpanel"
        aria-label="Recepcionista IA"
      >
        <div className={styles.sectionHeading}>
          <div>
            <span className={`${styles.sectionIcon} ${styles.aiIcon}`}>
              <Bot size={19} aria-hidden="true" />
            </span>
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
              readOnly={!canManage}
              onChange={event => updateAgent("name", event.target.value)}
            />
          </Field>

          <Field label="Idioma">
            <input
              value={draft.agent.language}
              readOnly={!canManage}
              onChange={event => updateAgent("language", event.target.value)}
            />
          </Field>

          <Field label="Voz" wide>
            <select
              value={draft.agent.voice}
              disabled={!canManage}
              onChange={event => updateAgent("voice", event.target.value)}
            >
              {!voices.some(option =>
                (option.selection || option.code) === draft.agent.voice
              ) && draft.agent.voice && (
                <option value={draft.agent.voice}>{draft.agent.voice}</option>
              )}
              {voices.map(option => {
                const value = String(option.selection || option.code || "");
                return <option key={value} value={value}>{option.name || value}</option>;
              })}
            </select>
            <small>Elige la voz para las próximas llamadas. La voz actual se conserva hasta que guardes los cambios.</small>
          </Field>

          <Field label="Saludo inicial" wide>
            <textarea
              value={draft.agent.greeting}
              readOnly={!canManage}
              onChange={event => updateAgent("greeting", event.target.value)}
            />
          </Field>

          <Field label="Instrucciones conversacionales" wide>
            <textarea
              className={styles.tallTextarea}
              value={draft.agent.instructions}
              readOnly={!canManage}
              onChange={event => updateAgent("instructions", event.target.value)}
            />
          </Field>
        </div>

        <label className={styles.agentToggle}>
          <input
            type="checkbox"
            checked={draft.agent.active}
            disabled={!canManage}
            onChange={event => updateAgent("active", event.target.checked)}
          />
          <span>
            <strong>Agente IA activo para este negocio</strong>
            <small>Desactívalo para impedir que atienda aunque la configuración permanezca guardada.</small>
          </span>
        </label>

        <div className={styles.capabilityBlock}>
          <div>
            <strong>Capacidades permitidas</strong>
            <span>El backend sigue aplicando estas restricciones aunque el modelo intente excederlas.</span>
          </div>
          <div className={styles.capabilityGrid}>
            {agentCapabilities.map(([value, label]) => {
              const checked = draft.agent.capabilities.includes(value);
              return (
                <label className={styles.capabilityOption} key={value}>
                  <input
                    type="checkbox"
                    checked={checked}
                    disabled={!canManage}
                    onChange={event => {
                      const next = event.target.checked
                        ? [...new Set([...draft.agent.capabilities, value])]
                        : draft.agent.capabilities.filter(item => item !== value);
                      updateAgent("capabilities", next);
                    }}
                  />
                  <span>{label}</span>
                </label>
              );
            })}
          </div>
        </div>

        <div className={styles.authorityNote}>
          <ShieldCheck size={18} aria-hidden="true" />
          <div>
            <strong>La IA no decide precios, stock, disponibilidad ni pagos.</strong>
            <span>Esos datos se consultan desde servicios autoritativos del backend.</span>
          </div>
        </div>
      </section>
    );
  }

  if (activeSection === "services") {
    return (
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
          {canManage && (
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
                    readOnly={!canManage}
                    onChange={event => updateService(index, { name: event.target.value })}
                  />
                </Field>
                <Field label="Duración (min)">
                  <input
                    type="number"
                    min="1"
                    value={service.durationMinutes}
                    readOnly={!canManage}
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
                    readOnly={!canManage}
                    onChange={event => updateService(index, {
                      price: event.target.value === "" ? null : Number(event.target.value)
                    })}
                  />
                </Field>
                <Field label="Descripción" wide>
                  <input
                    value={String(service.description ?? "")}
                    readOnly={!canManage}
                    onChange={event => updateService(index, { description: event.target.value })}
                  />
                </Field>
              </div>
              {canManage && (
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
    );
  }

  if (activeSection === "hours") {
    return (
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
          {canManage && (
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
                disabled={!canManage}
                onChange={event => updateHour(index, { dayOfWeek: Number(event.target.value) })}
              >
                {dayNames.map((name, day) => <option value={day} key={name}>{name}</option>)}
              </select>
              <input
                aria-label={`Apertura ${index + 1}`}
                type="time"
                value={String(hour.openTime).slice(0, 5)}
                readOnly={!canManage}
                onChange={event => updateHour(index, { openTime: event.target.value })}
              />
              <span aria-hidden="true">→</span>
              <input
                aria-label={`Cierre ${index + 1}`}
                type="time"
                value={String(hour.closeTime).slice(0, 5)}
                readOnly={!canManage}
                onChange={event => updateHour(index, { closeTime: event.target.value })}
              />
              {canManage && (
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

        <ScheduleExceptionsPanel
          canRead={canReadScheduleExceptions}
          canManage={canManageScheduleExceptions}
        />
      </section>
    );
  }

  if (activeSection === "knowledge") {
    return (
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
          {canManage && (
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
                    readOnly={!canManage}
                    onChange={event => updateKnowledge(index, { title: event.target.value })}
                  />
                </Field>
                <Field label="Categoría">
                  <input
                    value={String(item.category ?? "")}
                    readOnly={!canManage}
                    onChange={event => updateKnowledge(index, { category: event.target.value })}
                  />
                </Field>
                <Field label="Respuesta" wide>
                  <textarea
                    value={item.content}
                    readOnly={!canManage}
                    onChange={event => updateKnowledge(index, { content: event.target.value })}
                  />
                </Field>
              </div>
              {canManage && (
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
    );
  }

  if (activeSection === "channels") {
    return (
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
              <p>Telefonía y WhatsApp Business con acciones explícitas y sin efectos automáticos al abrir la pantalla.</p>
            </div>
          </div>
        </div>

        <ChannelsSettingsPanel
          phones={phones}
          canRead={canReadChannels}
          canManage={canManageChannels}
        />
      </section>
    );
  }

  if (activeSection === "integrations") {
    return (
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
              <p>Conecta capacidades comerciales sin exponer credenciales ni mezclar herramientas internas.</p>
            </div>
          </div>
        </div>

        <IntegrationsSettingsPanel
          canRead={canReadIntegrations}
          canManage={canManageIntegrations}
        />
      </section>
    );
  }

  return (
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
<TeamSettingsPanel canManage={canManageTeam} />
    </section>
  );
}
