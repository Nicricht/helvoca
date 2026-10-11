import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, ClipboardCheck, PhoneCall, ShieldCheck } from "lucide-react";
import { useState } from "react";
import {
  getPhoneVoiceReadiness,
  type PhoneVoiceReadiness
} from "../../features/settings/api";
import styles from "./SettingsPage.module.css";

type Stage = "done" | "warning" | "blocked";

function stageFor(state: string | undefined): Stage {
  if (state === "PREVIOUSLY_VERIFIED") return "done";
  if (state === "ACTIVATION_REQUIRED" || state === "NEEDS_VERIFICATION") return "warning";
  return "blocked";
}

export function VoiceSetupGuide({
  canRead,
  canManage,
  onVerifyAndActivate,
  activationPending
}: {
  canRead: boolean;
  canManage: boolean;
  onVerifyAndActivate: (id: string) => void;
  activationPending: boolean;
}) {
  const [selectedPhoneId, setSelectedPhoneId] = useState("");
  const readiness = useQuery({
    queryKey: ["settings", "phone-voice-readiness"],
    queryFn: getPhoneVoiceReadiness,
    enabled: canRead,
    retry: false,
    refetchOnWindowFocus: false
  });

  const entries = readiness.data ?? [];
  const selected = entries.find(item => item.phoneId === selectedPhoneId) ?? entries[0];
  const stage = selected ? stageFor(selected.state) : "blocked";

  return (
    <div className={styles.voiceSetupGuide} aria-label="Guía de configuración de llamadas">
      <div className={styles.voiceSetupHeading}>
        <div>
          <strong>Configura tu recepcionista telefónica</strong>
          <p>Tres pasos para preparar tu número sin dar por hecho que las llamadas ya funcionan.</p>
        </div>
        <ShieldCheck size={20} aria-hidden="true" />
      </div>
      <div className={styles.voiceSetupSteps}>
        <div className={styles.voiceSetupStep}>
          <span className={styles.voiceSetupStepTitle}>1. Número del negocio</span>
          <strong>{selected ? "Número registrado" : "Número pendiente"}</strong>
          <span>Conecta uno ya asignado a tu negocio o busca uno nuevo más abajo.</span>
        </div>
        <div className={styles.voiceSetupStep}>
          <span className={styles.voiceSetupStepTitle}>2. Identidad y voz</span>
          <strong>{stage === "done" ? "Verificado anteriormente" : stage === "warning" ? "Requiere verificación" : "No certificado"}</strong>
          <span>La propiedad, el SID y los webhooks deben coincidir con el proveedor.</span>
        </div>
        <div className={styles.voiceSetupStep}>
          <span className={styles.voiceSetupStepTitle}>3. Prueba de funcionamiento</span>
          <strong>Certificación pendiente</strong>
          <span>Antes de usar llamadas reales hay que certificar la conexión y la IA.</span>
        </div>
      </div>

      {!canRead ? (
        <p className={styles.voiceSetupNotice} role="status">
          No tienes permisos para consultar el diagnóstico de telefonía.
        </p>
      ) : readiness.isPending ? (
        <p className={styles.voiceSetupNotice} role="status">Consultando el estado registrado…</p>
      ) : readiness.isError ? (
        <div className={styles.voiceSetupAlert} role="alert">
          <AlertTriangle size={16} aria-hidden="true" />
          <span>No se pudo verificar el estado. No marques el número como listo para recibir llamadas.</span>
          <button type="button" className="button ghost" onClick={() => void readiness.refetch()}>
            Reintentar consulta
          </button>
        </div>
      ) : entries.length === 0 ? (
        <p className={styles.voiceSetupNotice} role="status">
          Aún no hay números asignados a este negocio. Usa las opciones de conexión o búsqueda para comenzar.
        </p>
      ) : (
        <div className={styles.voiceSetupDetails}>
          <label className={styles.voiceSetupLabel} htmlFor="voice-setup-phone">
            Número que quieres preparar
          </label>
          <select
            id="voice-setup-phone"
            className={styles.voiceSetupSelect}
            value={selected?.phoneId ?? ""}
            onChange={event => setSelectedPhoneId(event.target.value)}
          >
            {entries.map(item => (
              <option value={item.phoneId} key={item.phoneId}>
                {item.phoneNumber}
              </option>
            ))}
          </select>

          <div className={styles.voiceSetupStatus} role="status">
            {stage === "done" ? (
              <CheckCircle2 size={18} aria-hidden="true" />
            ) : (
              <AlertTriangle size={18} aria-hidden="true" />
            )}
            <div>
              <strong>{selected?.message}</strong>
              <span>
                {selected?.lastVerifiedAt
                  ? "Última verificación registrada: " + new Date(selected.lastVerifiedAt).toLocaleString("es-CL")
                  : "Sin evidencia de verificación de voz registrada."}
              </span>
            </div>
          </div>
          {canManage && selected?.canAttemptActivation && (
            <button
              type="button"
              className="button secondary"
              disabled={activationPending}
              onClick={() => onVerifyAndActivate(selected.phoneId)}
            >
              <ClipboardCheck size={16} aria-hidden="true" />
              {activationPending ? "Validando…" : "Verificar con Twilio y activar"}
            </button>
          )}
          <p className={styles.voiceSetupNotice}>
            <PhoneCall size={15} aria-hidden="true" />
            Esta consulta es de solo lectura y no realiza llamadas, compras ni activaciones.
            La verificación con Twilio requiere una acción explícita del administrador.
            Ningún estado de esta pantalla equivale a una prueba real de llamada.
          </p>
        </div>
      )}
    </div>
  );
}
