import { useEffect, useRef, useState, type FormEvent } from "react";
import { KeyRound, LockKeyhole, ShieldCheck, Sparkles, UserRoundCheck } from "lucide-react";
import { TOKEN_KEY } from "../../api/client";
import {
  acceptInvitation,
  previewInvitation,
  type PublicInvitation
} from "../../features/invite/api";
import styles from "./InvitePage.module.css";

function roleLabel(role: string) {
  const labels: Record<string, string> = {
    BUSINESS_OWNER: "Propietario",
    BUSINESS_ADMIN: "Administrador",
    MANAGER: "Encargado",
    RECEPTION: "Recepción / Caja",
    STAFF: "Personal",
    KITCHEN: "Preparación / Cocina",
    DISPATCH: "Despacho",
    PROFESSIONAL: "Profesional",
    WAREHOUSE: "Bodega / Inventario",
    SALES: "Ventas",
    OPERATOR: "Operador"
  };
  return labels[role] || role || "Sin rol";
}

function statusMessage(status: string) {
  if (status === "ACCEPTED") return "Esta invitación ya fue utilizada.";
  if (status === "EXPIRED") return "Esta invitación expiró. Pide una nueva.";
  if (status === "REVOKED") return "Esta invitación fue revocada.";
  return "Esta invitación no está disponible.";
}

export function InvitePage() {
  const params = new URLSearchParams(window.location.search);
  const businessId = params.get("businessId")?.trim() || "";
  const token = params.get("token")?.trim() || "";

  const submitLock = useRef(false);
  const [invitation, setInvitation] = useState<PublicInvitation | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "invalid">(
    businessId && token ? "loading" : "invalid"
  );
  const [title, setTitle] = useState(
    businessId && token ? "Cargando invitación…" : "Enlace incompleto"
  );
  const [subtitle, setSubtitle] = useState(
    businessId && token
      ? "Estamos verificando que este enlace siga vigente."
      : "Pide al administrador que genere una nueva invitación."
  );
  const [message, setMessage] = useState("");
  const [messageKind, setMessageKind] = useState<"error" | "success">("error");
  const [accepted, setAccepted] = useState(false);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    let active = true;
    if (!businessId || !token) return;

    previewInvitation(businessId, token)
      .then(data => {
        if (!active) return;
        setInvitation(data);
        setTitle("Únete a " + (data.businessName || "este negocio"));
        setSubtitle("Crea tu contraseña para entrar a RecepVoz.");
        setState("ready");

        if (String(data.status || "").toUpperCase() !== "PENDING") {
          setMessage(statusMessage(String(data.status || "").toUpperCase()));
          setMessageKind("error");
        }
      })
      .catch(error => {
        if (!active) return;
        setInvitation(null);
        setState("invalid");
        setTitle("Invitación no válida");
        setSubtitle("El enlace puede haber expirado o sido reemplazado.");
        setMessage(error instanceof Error ? error.message : "La invitación no está disponible.");
        setMessageKind("error");
      });

    return () => {
      active = false;
    };
  }, [businessId, token]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (
      submitLock.current ||
      pending ||
      !invitation ||
      String(invitation.status || "").toUpperCase() !== "PENDING"
    ) {
      return;
    }

    const form = new FormData(event.currentTarget);
    const password = String(form.get("password") || "");
    const confirmPassword = String(form.get("confirmPassword") || "");

    if (password !== confirmPassword) {
      setMessage("Las contraseñas no coinciden.");
      setMessageKind("error");
      return;
    }

    submitLock.current = true;
    setPending(true);
    setMessage("");

    try {
      const result = await acceptInvitation(businessId, token, password);
      sessionStorage.setItem(TOKEN_KEY, result.accessToken);
      setAccepted(true);
      setMessage("Invitación aceptada. Entrando a RecepVoz…");
      setMessageKind("success");
      window.setTimeout(() => {
        window.location.replace("/app");
      }, 900);
    } catch (error) {
      submitLock.current = false;
      setPending(false);
      setMessage(error instanceof Error ? error.message : "No fue posible aceptar la invitación.");
      setMessageKind("error");
    }
  }

  const status = String(invitation?.status || "").toUpperCase();
  const canAccept = state === "ready" && status === "PENDING" && !accepted;

  return (
    <div className={styles.page} data-public-auth-surface="invite">
      <div className={styles.gridGlow} aria-hidden="true" />

      <main className={styles.shell}>
        <section className={styles.brandRail}>
          <div className={styles.brand}>
            <span className={styles.brandMark}><Sparkles size={20} aria-hidden="true" /></span>
            <span><strong>RecepVoz</strong><small>Tu recepcionista IA</small></span>
          </div>

          <div className={styles.securityCard}>
            <ShieldCheck size={20} aria-hidden="true" />
            <div>
              <strong>Invitación personal</strong>
              <p>Este enlace es de un solo uso. Tu contraseña la defines tú y no se comparte con quien te invitó.</p>
            </div>
          </div>

          <div className={styles.trustList}>
            <div><LockKeyhole size={16} /><span>Contraseña privada</span></div>
            <div><KeyRound size={16} /><span>Acceso de un solo uso</span></div>
            <div><UserRoundCheck size={16} /><span>Rol asignado por tu negocio</span></div>
          </div>
        </section>

        <section className={styles.card}>
          <p className={styles.eyebrow}>INVITACIÓN DE EQUIPO</p>
          <h1 id="inviteTitle">{title}</h1>
          <p id="inviteSubtitle" className={styles.subtitle}>{subtitle}</p>

          {invitation && (
            <div id="inviteMeta" className={styles.meta}>
              <strong>{invitation.name || invitation.email || "Invitación"}</strong>
              <span>{invitation.email || ""} · {roleLabel(invitation.role)}</span>
            </div>
          )}

          {canAccept && (
            <form id="inviteAcceptForm" className={styles.form} onSubmit={submit}>
              <label>
                <span>Nueva contraseña</span>
                <input
                  name="password"
                  type="password"
                  minLength={10}
                  maxLength={72}
                  autoComplete="new-password"
                  required
                />
              </label>
              <label>
                <span>Repite la contraseña</span>
                <input
                  name="confirmPassword"
                  type="password"
                  minLength={10}
                  maxLength={72}
                  autoComplete="new-password"
                  required
                />
              </label>
              <p className={styles.passwordHint}>Usa entre 10 y 72 caracteres.</p>
              <button className={styles.primaryButton} type="submit" disabled={pending}>
                {pending ? "Aceptando…" : "Aceptar invitación"}
              </button>
            </form>
          )}

          {state === "loading" && (
            <div className={styles.loading} role="status">Verificando invitación…</div>
          )}

          {message && (
            <div
              id="inviteMessage"
              className={messageKind === "success" ? styles.success : styles.error}
              role={messageKind === "error" ? "alert" : "status"}
            >
              {message}
            </div>
          )}

          {accepted && (
            <div className={styles.acceptedCard}>
              <UserRoundCheck size={18} aria-hidden="true" />
              <span>Tu cuenta quedó activada. Abriendo tu espacio de trabajo…</span>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
