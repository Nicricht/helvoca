import { useEffect, useState, type FormEvent } from "react";
import { CheckCircle2, KeyRound, RefreshCw, ShieldCheck } from "lucide-react";
import { setAccessToken } from "../../api/client";
import {
  confirmSingleOperatorEnrollment,
  getCurrentSingleOperatorEnrollment,
  loginPlatformAdmin,
  prepareSingleOperatorEnrollment,
  type TwilioEnrollmentView
} from "../../features/platform/api";
import styles from "./PlatformPage.module.css";

const AURORA_PHONE_NUMBER = "+16812706780";

function messageOf(value: unknown) {
  return value instanceof Error ? value.message : "No fue posible completar la operación.";
}

export function TwilioEnrollmentPanel({ email }: { email: string }) {
  const [enrollment, setEnrollment] = useState<TwilioEnrollmentView>({
    changeId: null,
    state: "LOADING"
  });
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    getCurrentSingleOperatorEnrollment()
      .then((current) => {
        if (active) setEnrollment(current);
      })
      .catch((error: unknown) => {
        if (!active) return;
        setEnrollment({ changeId: null, state: "UNAVAILABLE" });
        setMessage("No se pudo leer el estado del enrolamiento: " + messageOf(error));
      });
    return () => {
      active = false;
    };
  }, []);

  async function refreshStatus() {
    if (busy) return;
    setBusy(true);
    setMessage("");
    try {
      setEnrollment(await getCurrentSingleOperatorEnrollment());
    } catch (error) {
      setMessage("No se pudo actualizar el estado: " + messageOf(error));
    } finally {
      setBusy(false);
    }
  }

  async function prepareEnrollment() {
    if (busy) return;
    setBusy(true);
    setMessage("");
    try {
      const requested = await prepareSingleOperatorEnrollment();
      setEnrollment(requested);
      setMessage(
        requested.state === "APPLIED"
          ? "La vinculación ya estaba aplicada. No se repitió."
          : "Solicitud preparada. Confirma con tu contraseña para aplicar la vinculación."
      );
    } catch (error) {
      try {
        const current = await getCurrentSingleOperatorEnrollment();
        if (current.state === "PENDING" || current.state === "APPLIED") {
          setEnrollment(current);
          setMessage("Se recuperó la solicitud existente. No se creó otra vinculación.");
          return;
        }
      } catch {
        // Preserve the original failure; no retry or external effect is attempted here.
      }
      setMessage(messageOf(error));
    } finally {
      setBusy(false);
    }
  }

  async function confirmEnrollment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !enrollment.changeId || !password || !email) return;
    setBusy(true);
    setMessage("");
    try {
      const session = await loginPlatformAdmin(email, password);
      if (!session.accessToken || !session.user?.roles?.includes("PLATFORM_ADMIN")) {
        throw new Error("La cuenta autenticada no tiene el rol PLATFORM_ADMIN.");
      }
      setAccessToken(session.accessToken);
      const result = await confirmSingleOperatorEnrollment(
        enrollment.changeId,
        password,
        AURORA_PHONE_NUMBER
      );
      setEnrollment(result);
      setPassword("");
      setMessage("Número 6780 vinculado a Aurora. No se inició ninguna llamada.");
    } catch (error) {
      setPassword("");
      setMessage(messageOf(error));
    } finally {
      setBusy(false);
    }
  }

  const state = enrollment.state.toUpperCase();
  const canPrepare = state === "NOT_STARTED" || state === "EXPIRED";

  return (
    <div className={styles.enrollmentPanel}>
      <div className={styles.enrollmentSummary}>
        <ShieldCheck size={18} aria-hidden="true" />
        <div>
          <strong>Centro Médico Aurora Sur · número saliente terminado en 6780</strong>
          El servidor verifica la titularidad directamente con Twilio. Tu contraseña solo se utiliza para reautenticar al administrador.
        </div>
      </div>

      <div className={styles.sessionState} role="status" aria-live="polite">
        {state === "LOADING" && "Consultando el estado guardado…"}
        {state === "UNAVAILABLE" && "El endpoint de enrolamiento no está disponible en este entorno. No se modificó el número."}
        {state === "NOT_STARTED" && "Todavía no existe una solicitud de vinculación."}
        {state === "EXPIRED" && "La solicitud anterior venció. Puedes preparar una nueva sin duplicar el inventario."}
        {state === "PENDING" && "La solicitud está preparada y espera tu confirmación autenticada."}
        {state === "APPLIED" && "La vinculación del número está aplicada y registrada."}
      </div>

      {(state === "PENDING" || state === "APPLIED") && enrollment.changeId && (
        <div className={styles.sessionFacts}>
          <article><span>Negocio</span><strong>Aurora Sur</strong></article>
          <article><span>Número</span><strong>•••• 6780</strong></article>
          <article><span>Solicitud</span><strong>{state}</strong></article>
        </div>
      )}

      {state === "APPLIED" && (
        <div className={styles.message} role="status">
          <CheckCircle2 size={14} /> Vinculación confirmada. El runner, Gemini y las llamadas siguen desactivados.
        </div>
      )}

      {canPrepare && (
        <div className={styles.formActions}>
          <button
            id="platformTwilioPrepare"
            className={styles.primaryButton}
            type="button"
            disabled={busy}
            onClick={prepareEnrollment}
          >
            {busy ? "Verificando…" : state === "EXPIRED" ? "Preparar nueva confirmación" : "Preparar vinculación"}
          </button>
        </div>
      )}

      {state === "PENDING" && (
        <form className={styles.enrollmentForm} onSubmit={confirmEnrollment}>
          <label>
            Contraseña actual de tu cuenta de plataforma
            <input
              id="platformTwilioAdminPassword"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </label>
          <button
            id="platformTwilioConfirm"
            className={styles.primaryButton}
            type="submit"
            disabled={busy || !password || !email}
          >
            <KeyRound size={14} />
            {busy ? "Confirmando…" : "Confirmar vinculación"}
          </button>
        </form>
      )}

      <div className={styles.formActions}>
        <button
          id="platformTwilioRefresh"
          className={styles.ghostButton}
          type="button"
          onClick={refreshStatus}
          disabled={busy}
        >
          <RefreshCw size={14} /> Actualizar estado
        </button>
      </div>

      <div className={styles.truthBox}>
        Esta pantalla solo registra y confirma la titularidad del número. No activa el runner, Gemini Live ni la ruta de llamadas y no genera cargos.
      </div>
      <div className={styles.message} role="status" aria-live="polite">{message}</div>
    </div>
  );
}
