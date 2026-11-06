import { ArrowRight, Bot, CheckCircle2, ClipboardCheck, CloudUpload, LockKeyhole, Settings2, ShieldCheck } from "lucide-react";
import { Link } from "react-router-dom";
import type { OnboardingStatus } from "../../features/settings/api";
import styles from "./SettingsExpressIntro.module.css";

type GuidedSection = "business" | "services" | "hours" | "channels" | "knowledge" | "receptionist";

const guidedSteps: Record<string, { section: GuidedSection; title: string; action: string; detail: string }> = {
  CONFIGURE_BUSINESS: {
    section: "business", title: "Completa los datos del negocio", action: "Ir a Negocio",
    detail: "Revisa nombre, rubro y datos de atención antes de guardar."
  },
  ADD_SERVICE: {
    section: "services", title: "Agrega los servicios del negocio", action: "Ir a Servicios",
    detail: "Incluye únicamente servicios, duraciones y precios que puedas confirmar."
  },
  CONFIGURE_HOURS: {
    section: "hours", title: "Define los horarios de atención", action: "Ir a Horarios",
    detail: "Anota los horarios reales de tu negocio, sin asumir días disponibles."
  },
  CONNECT_PHONE_NUMBER: {
    section: "channels", title: "Revisa la conexión telefónica", action: "Ir a Canales",
    detail: "La configuración del número no equivale a una llamada real certificada."
  },
  OPTIONAL_HUMAN_TRANSFER: {
    section: "business", title: "Decide si necesitas derivación humana", action: "Ir a Negocio",
    detail: "Este paso es opcional: solo utiliza un número autorizado para transferencias."
  },
  OPTIONAL_KNOWLEDGE: {
    section: "knowledge", title: "Agrega preguntas frecuentes si las tienes", action: "Ir a Conocimiento",
    detail: "Es opcional. Añade únicamente respuestas confirmadas por tu negocio."
  },
  READY: {
    section: "receptionist", title: "Base configurada: revisa la recepcionista", action: "Ir a Recepcionista IA",
    detail: "La base está configurada, pero esto no certifica llamadas ni WhatsApp reales."
  }
};

type Props = {
  businessName: string;
  status?: OnboardingStatus | null;
  serviceCount: number;
  knowledgeCount: number;
  canImport: boolean;
  onAdvanced: () => void;
  onContinue: (section: GuidedSection) => void;
};

export function SettingsExpressIntro({
  businessName, status, serviceCount, knowledgeCount, canImport, onAdvanced, onContinue
}: Props) {
  const profileReady = status?.businessProfileConfigured === true;
  const contentReady = status?.servicesConfigured === true
    && status?.knowledgeConfigured === true && status?.scheduleConfigured === true;
  const phoneReady = status?.phoneConfigured === true;
  const nextStep = status?.nextStep ? guidedSteps[status.nextStep] : undefined;
  return (
    <section className={styles.express} aria-labelledby="expressTitle" data-testid="settings-express">
      <div className={styles.head}>
        <div>
          <span className={styles.kicker}><Bot size={15} aria-hidden="true" /> CONFIGURACIÓN EXPRÉS</span>
          <h2 id="expressTitle">Tu negocio, preparado con menos trabajo.</h2>
          <p>Entrega fotos, catálogos o documentos que ya tienes. RecepVoz prepara un borrador y tú decides qué guardar.</p>
        </div>
        <span className={styles.summary}>
          <ShieldCheck size={16} aria-hidden="true" /> Revisión antes de guardar
        </span>
      </div>
      <ol className={styles.steps} aria-label="Preparación en tres pasos">
        <li className={styles.stepPrimary}>
          <span className={styles.number}>01</span>
          <CloudUpload size={20} aria-hidden="true" />
          <div>
            <strong>Importar</strong>
            <p>Fotos, PDF, Excel o CSV. Sin volver a escribir tu catálogo.</p>
          </div>
          {canImport ? (
            <Link className={styles.action} to="/settings/import">
              Empezar <ArrowRight size={15} aria-hidden="true" />
            </Link>
          ) : (
            <span className={styles.restricted}><LockKeyhole size={14} aria-hidden="true" /> Solo administración</span>
          )}
        </li>
        <li>
          <span className={styles.number}>02</span>
          <ClipboardCheck size={20} aria-hidden="true" />
          <div>
            <strong>Revisar</strong>
            <p>Compara, corrige y aprueba únicamente los datos importantes.</p>
          </div>
        </li>
        <li>
          <span className={styles.number}>03</span>
          <CheckCircle2 size={20} aria-hidden="true" />
          <div>
            <strong>Preparar</strong>
            <p>Revisa qué falta antes de conectar canales y probar la atención.</p>
          </div>
        </li>
      </ol>
      <section className={styles.guided} aria-label="Siguiente paso de configuración" data-testid="settings-next-step">
        <div className={styles.guidedContent}>
          <span className={styles.guidedTag}>SIGUIENTE PASO</span>
          <strong>{nextStep?.title ?? "No hay un siguiente paso verificado"}</strong>
          <p>{nextStep?.detail ?? "Revisa los ajustes manualmente. No activamos canales sin validar su estado."}</p>
          {nextStep && !canImport && (
            <span className={styles.guidedReadOnly}>Solo administración puede editar este paso</span>
          )}
        </div>
        {nextStep && canImport && (
          <button type="button" className={styles.guidedAction} onClick={() => onContinue(nextStep.section)}>
            {nextStep.action} <ArrowRight size={16} aria-hidden="true" />
          </button>
        )}
      </section>
      <div className={styles.bottom}>
        <div className={styles.facts}>
          <strong>{businessName || "Tu negocio"}</strong>
          <span>{serviceCount} servicio(s) · {knowledgeCount} fuente(s) de conocimiento</span>
          <span className={styles.checks}>
            <i data-ready={profileReady} /> Perfil {profileReady ? "configurado" : "por verificar"}
            <i data-ready={contentReady} /> Operación {contentReady ? "configurada" : "por verificar"}
            <i data-ready={phoneReady} /> Teléfono {phoneReady ? "registrado · verificar voz" : "por verificar"}
          </span>
        </div>
        <button className={styles.advanced} type="button" onClick={onAdvanced}>
          <Settings2 size={15} aria-hidden="true" /> Editar manualmente <ArrowRight size={14} aria-hidden="true" />
        </button>
      </div>
    </section>
  );
}
