import {
  Bot,
  Building2,
  CalendarClock,
  Globe2,
  MessagesSquare,
  Phone,
  Plus,
  ShieldCheck,
  Trash2,
  UsersRound,
  Wrench
} from "lucide-react";
import type { ReactNode } from "react";
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
  ["", "General"],
  ["store", "Tienda / comercio"],
  ["hardware_store", "Ferretería / materiales"],
  ["services", "Servicios"],
  ["restaurant", "Restaurant / alimentos"],
  ["clinic", "Salud / clínica"],
  ["salon", "Belleza / peluquería"],
  ["workshop", "Taller / reparación"],
  ["hospitality", "Hotel / hospedaje"],
  ["professional", "Profesional / oficina"]
] as const;

const businessPresetSuggestions: Record<string, string> = {
  store: "Prioriza productos.",
  hardware_store: "Prioriza catálogo, stock, cotizaciones, pedidos y despacho. Confirma medidas, cantidades y disponibilidad antes de cerrar una venta.",
  salon: "Prioriza servicios y reservas.",
  restaurant: "Prioriza productos y pedidos.",
  clinic: "Prioriza servicios y reservas."
};

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

          <Field label="Rubro">
            <select
              value={String(draft.profile.presetKey ?? "")}
              disabled={!canManage}
              onChange={event => updateProfile("presetKey", event.target.value || null)}
            >
              {businessPresetOptions.map(([value, label]) => (
                <option value={value} key={value || "general"}>{label}</option>
              ))}
            </select>
          </Field>

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
