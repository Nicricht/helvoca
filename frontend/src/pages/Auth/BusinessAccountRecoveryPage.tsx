import { useEffect, useRef, useState, type FormEvent } from "react";
import { ArrowLeft, KeyRound, LockKeyhole, MailCheck, ShieldCheck } from "lucide-react";
import { ApiError, apiRequest } from "../../api/client";
import styles from "./BusinessAccountRecoveryPage.module.css";

type Kind = "password" | "verification";
type Feedback = { text: string; kind: "error" | "success" } | null;

function initialFragmentToken(): string {
  const raw = new URLSearchParams(window.location.hash.slice(1)).get("token") || "";
  if (!raw) return "";
  return /^[A-Za-z0-9_-]{43}$/.test(raw) ? raw : "INVALID";
}

function serviceError(error: unknown): string {
  if (error instanceof ApiError && (error.status === 400 || error.status === 404)) {
    return "Este enlace es inválido o ha expirado. Solicita uno nuevo.";
  }
  if (error instanceof ApiError && error.status === 429) {
    return "Demasiados intentos. Espera un momento antes de volver a intentar.";
  }
  return "No pudimos completar la solicitud. Vuelve a intentarlo.";
}

export function BusinessAccountRecoveryPage({ kind }: { kind: Kind }) {
  // Secrets are never kept in persistent browser storage. After initial render
  // remove the fragment from history, before any user interaction or fetch.
  const [token, setToken] = useState(initialFragmentToken);
  const [busy, setBusy] = useState(false);
  const [completed, setCompleted] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>(null);
  const submitLock = useRef(false);
  const invalid = token === "INVALID";
  const isConfirming = Boolean(token) && !invalid && !completed;

  useEffect(() => {
    const title = document.title;
    document.title = kind === "password" ? "Recuperar contraseña · RecepVoz" : "Confirmar correo · RecepVoz";
    if (window.location.hash) {
      window.history.replaceState(window.history.state, "", window.location.pathname + window.location.search);
    }
    return () => { document.title = title; };
  }, [kind]);

  async function submitEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitLock.current) return;
    const form = new FormData(event.currentTarget);
    const email = String(form.get("email") || "").trim();
    submitLock.current = true;
    setBusy(true);
    setFeedback(null);
    try {
      await apiRequest<void>(kind === "password"
        ? "/api/v1/auth/password-reset/request"
        : "/api/v1/auth/email/resend", {
        method: "POST",
        body: JSON.stringify({ email })
      }, false);
      setFeedback({
        kind: "success",
        text: "Si existe una cuenta con ese correo, recibirás un enlace con las instrucciones."
      });
    } catch (error) {
      setFeedback({ kind: "error", text: serviceError(error) });
    } finally {
      submitLock.current = false;
      setBusy(false);
    }
  }

  async function submitToken(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitLock.current || !isConfirming) return;
    const form = new FormData(event.currentTarget);
    const newPassword = String(form.get("password") || "");
    if (kind === "password" && newPassword !== String(form.get("repeatPassword") || "")) {
      setFeedback({ kind: "error", text: "Las contraseñas no coinciden." });
      return;
    }
    submitLock.current = true;
    setBusy(true);
    setFeedback(null);
    try {
      await apiRequest<void>(kind === "password"
        ? "/api/v1/auth/password-reset/confirm"
        : "/api/v1/auth/email/confirm", {
        method: "POST",
        body: JSON.stringify(kind === "password" ? { token, newPassword } : { token })
      }, false);
      setToken("");
      setCompleted(true);
      setFeedback({ kind: "success", text: kind === "password"
        ? "Tu contraseña fue actualizada. Ya puedes ingresar a RecepVoz."
        : "Tu correo está confirmado. Ya puedes ingresar a RecepVoz." });
    } catch (error) {
      setFeedback({ kind: "error", text: serviceError(error) });
    } finally {
      submitLock.current = false;
      setBusy(false);
    }
  }

  const heading = completed
    ? (kind === "password" ? "Contraseña actualizada" : "Correo confirmado")
    : invalid ? "Enlace no válido"
      : isConfirming ? (kind === "password" ? "Crea una nueva contraseña" : "Confirma tu correo")
        : (kind === "password" ? "Recupera tu acceso" : "Reenviar confirmación");

  return (
    <main className={styles.page} data-account-self-service={kind}>
      <div className={styles.ambient} aria-hidden="true" />
      <div className={styles.shell}>
        <a className={styles.brand} href="/app/auth"><span className={styles.logo}>✦</span> Recep<span>Voz</span></a>
        <section className={styles.card}>
          <div className={styles.icon} aria-hidden="true">
            {kind === "password" ? <KeyRound size={25} /> : <MailCheck size={25} />}
          </div>
          <span className={styles.eyebrow}>ACCESO SEGURO</span>
          <h1>{heading}</h1>
          <p className={styles.description}>
            {invalid ? "El enlace está incompleto o tiene un formato inválido."
              : completed ? "Tu cuenta está lista para continuar."
                : isConfirming ? "Este enlace es personal y se utiliza una sola vez."
                  : kind === "password"
                    ? "Indica el correo de tu cuenta. Te enviaremos instrucciones si corresponde."
                    : "Si todavía no recibiste el mensaje, puedes solicitar otro enlace."}
          </p>

          {!completed && !invalid && !isConfirming && (
            <form className={styles.form} onSubmit={submitEmail} aria-busy={busy}>
              <label htmlFor="accountRecoveryEmail">Correo de la cuenta</label>
              <input id="accountRecoveryEmail" name="email" type="email" autoComplete="email"
                placeholder="nombre@negocio.cl" maxLength={180} required />
              <button className={styles.submit} type="submit" disabled={busy}>
                {kind === "password" ? "Enviar enlace" : "Reenviar confirmación"}
              </button>
            </form>
          )}

          {isConfirming && (
            <form className={styles.form} onSubmit={submitToken} aria-busy={busy}>
              {kind === "password" && (
                <>
                  <label htmlFor="recoveryPassword">Nueva contraseña</label>
                  <input id="recoveryPassword" name="password" type="password"
                    minLength={10} maxLength={72} autoComplete="new-password" required />
                  <label htmlFor="recoveryPasswordRepeat">Repite la contraseña</label>
                  <input id="recoveryPasswordRepeat" name="repeatPassword" type="password"
                    minLength={10} maxLength={72} autoComplete="new-password" required />
                </>
              )}
              <button className={styles.submit} type="submit" disabled={busy}>
                {kind === "password" ? "Guardar contraseña" : "Confirmar correo"}
              </button>
            </form>
          )}

          {invalid && (
            <div className={styles.alert} role="alert">Solicita un enlace nuevo desde tu correo.</div>
          )}
          {feedback && (
            <div role={feedback.kind === "error" ? "alert" : "status"}
              className={feedback.kind === "error" ? styles.alert : styles.success}>
              {feedback.text}
            </div>
          )}
          <div className={styles.links}>
            <a href="/app/auth"><ArrowLeft size={15} /> Volver a ingresar</a>
            {(completed || invalid || isConfirming) && (
              <a href={kind === "password" ? "/app/password-reset" : "/app/verify-email"}>
                Solicitar otro enlace
              </a>
            )}
          </div>
        </section>
        <p className={styles.safety}><ShieldCheck size={15} /> Nunca compartas contraseñas ni enlaces de acceso.</p>
        <span className={styles.footIcon}><LockKeyhole size={14} /> Enlaces privados y de un solo uso</span>
      </div>
    </main>
  );
}
