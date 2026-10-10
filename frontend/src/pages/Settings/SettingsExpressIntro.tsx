import { ArrowRight, Bot, CheckCircle2, ClipboardCheck, CloudUpload, LockKeyhole, Settings2, ShieldCheck } from "lucide-react";
import { Link } from "react-router-dom";
import type { OnboardingStatus } from "../../features/settings/api";
import styles from "./SettingsExpressIntro.module.css";

type Props = {
  businessName: string;
  status?: OnboardingStatus | null;
  serviceCount: number;
  knowledgeCount: number;
  canImport: boolean;
  onAdvanced: () => void;
};

export function SettingsExpressIntro({
  businessName, status, serviceCount, knowledgeCount, canImport, onAdvanced
}: Props) {
  const profileReady = status?.businessProfileConfigured === true;
  const contentReady = status?.servicesConfigured === true
    && status?.knowledgeConfigured === true && status?.scheduleConfigured === true;
  const phoneReady = status?.phoneConfigured === true;
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
      <div className={styles.bottom}>
        <div className={styles.facts}>
          <strong>{businessName || "Tu negocio"}</strong>
          <span>{serviceCount} servicio(s) · {knowledgeCount} fuente(s) de conocimiento</span>
          <span className={styles.checks}>
            <i data-ready={profileReady} /> Perfil {profileReady ? "configurado" : "por verificar"}
            <i data-ready={contentReady} /> Operación {contentReady ? "configurada" : "por verificar"}
            <i data-ready={phoneReady} /> Teléfono {phoneReady ? "configurado" : "por verificar"}
          </span>
        </div>
        <button className={styles.advanced} type="button" onClick={onAdvanced}>
          <Settings2 size={15} aria-hidden="true" /> Editar manualmente <ArrowRight size={14} aria-hidden="true" />
        </button>
      </div>
    </section>
  );
}
