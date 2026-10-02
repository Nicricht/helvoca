import {
  Bot,
  Building2,
  CalendarClock,
  Globe2,
  Link2,
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

          <Field label="Moneda">
            <input
              maxLength={3}
              value={String(draft.profile.defaultCurrency ?? "CLP")}
              readOnly={!canManage}
              onChange={event => updateProfile("defaultCurrency", event.target.value)}
            />
          </Field>
        </div>
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
              <p>Estado observable de tus números. Las acciones de proveedor se mantienen separadas y explícitas.</p>
            </div>
          </div>
        </div>

        <div className={styles.channelList}>
          {phones.length === 0 && (
            <div className={styles.emptyCard}>
              <Phone size={20} aria-hidden="true" />
              <div>
                <strong>No hay números conectados.</strong>
                <span>Esta pantalla no aprovisiona ni activa proveedores automáticamente.</span>
              </div>
            </div>
          )}
          {phones.map(phone => (
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
              <p>Los proveedores externos se administran con endpoints explícitos y nunca muestran credenciales completas.</p>
            </div>
          </div>
        </div>
        <div className={styles.authorityNote}>
          <ShieldCheck size={18} aria-hidden="true" />
          <div>
            <strong>Zona segura de integraciones</strong>
            <span>
              Activación de WhatsApp, pagos y otros proveedores se migra como bloque independiente
              con confirmación, permisos y E2E mockeado.
            </span>
          </div>
        </div>
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
