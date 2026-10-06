import { useEffect, useMemo, useState, type FormEvent } from "react";
import { MotionConfig, motion } from "framer-motion";
import {
  ArrowRight,
  BarChart3,
  CalendarDays,
  Check,
  LockKeyhole,
  MessageCircle,
  PhoneCall,
  ShieldCheck,
  Sparkles
} from "lucide-react";
import {
  ApiError,
  apiRequest,
  clearAccessToken,
  getAccessToken,
  setAccessToken
} from "../../api/client";
import styles from "./AuthPage.module.css";

type AuthMode = "register" | "login";

type AuthResponse = {
  accessToken: string;
  user?: {
    email?: string;
    roles?: string[];
  };
};

type MeResponse = {
  email?: string;
  roles?: string[];
};

function detectedTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "America/Santiago";
  } catch {
    return "America/Santiago";
  }
}

function detectedLanguage(): string {
  const language = (navigator.language || "es").toLowerCase().split("-")[0];
  return /^[a-z]{2,3}$/.test(language) ? language : "es";
}

function destinationForRoles(roles?: string[]): string {
  return Array.isArray(roles) && roles.includes("PLATFORM_ADMIN") ? "/app/platform" : "/app";
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.message.trim()) return error.message;
  if (error instanceof Error && error.message.trim()) return error.message;
  return fallback;
}

function BrandMark() {
  return (
    <span className="rv-brand-mark" aria-label="RecepVoz">
      <svg className="rv-brand-logo" viewBox="0 0 84 48" aria-hidden="true">
        <defs>
          <linearGradient id="authBrandGradient" x1="0" x2="1">
            <stop offset="0" stopColor="#16d9f5" />
            <stop offset=".52" stopColor="#2f7cff" />
            <stop offset="1" stopColor="#8b5cf6" />
          </linearGradient>
        </defs>
        <g fill="url(#authBrandGradient)">
          <rect x="3" y="18" width="8" height="14" rx="4" />
          <rect x="15" y="11" width="8" height="28" rx="4" />
          <rect x="27" y="4" width="8" height="40" rx="4" />
          <rect x="39" y="14" width="8" height="20" rx="4" />
          <rect x="51" y="7" width="8" height="34" rx="4" />
          <rect x="63" y="13" width="8" height="22" rx="4" />
          <rect x="75" y="19" width="6" height="12" rx="3" />
        </g>
      </svg>
      <span className={styles.wordmark}>Recep<span>Voz</span></span>
    </span>
  );
}

function ProductFeature({
  feature,
  icon: Icon,
  title,
  detail
}: {
  feature: string;
  icon: typeof PhoneCall;
  title: string;
  detail: string;
}) {
  return (
    <motion.article
      className={`${styles.feature} rv-auth-feature`}
      data-feature={feature}
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: .32 }}
    >
      <svg className="rv-auth-feature-art" viewBox="0 0 72 52" aria-hidden="true">
        <rect x="5" y="7" width="62" height="38" rx="15" fill="rgba(22,217,245,.05)" stroke="rgba(22,217,245,.24)" />
        <path d="M16 34c8-12 15-17 22-15 7 2 11 12 18 4" fill="none" stroke="rgba(139,92,246,.75)" strokeWidth="3" strokeLinecap="round" />
        <circle cx="18" cy="31" r="3" fill="#16d9f5" />
        <circle cx="55" cy="23" r="3" fill="#8b5cf6" />
      </svg>
      <span className={styles.featureIcon}><Icon size={18} aria-hidden="true" /></span>
      <div><strong>{title}</strong><small>{detail}</small></div>
    </motion.article>
  );
}

export function AuthPage() {
  const [mode, setMode] = useState<AuthMode>("register");
  const [busy, setBusy] = useState(false);
  const [checkingSession, setCheckingSession] = useState(() => Boolean(getAccessToken()));
  const [message, setMessage] = useState("");

  const features = useMemo(() => [
    { feature: "calls", icon: PhoneCall, title: "Llamadas", detail: "Atención automática con contexto del negocio." },
    { feature: "calendar", icon: CalendarDays, title: "Agenda", detail: "Reservas y reagendamiento según disponibilidad." },
    { feature: "whatsapp", icon: MessageCircle, title: "WhatsApp", detail: "Habilitación controlada durante el piloto." },
    { feature: "analytics", icon: BarChart3, title: "Resultados", detail: "Actividad y valor atribuible visibles en tu panel." }
  ], []);

  useEffect(() => {
    const previousTitle = document.title;
    document.title = "RecepVoz · Tu negocio siempre contesta";

    const token = getAccessToken();
    if (!token) {
      setCheckingSession(false);
      return () => {
        document.title = previousTitle;
      };
    }

    let cancelled = false;
    fetch("/api/v1/auth/me", {
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${token}`
      }
    }).then(async response => {
      if (response.status === 401) {
        clearAccessToken();
        if (!cancelled) {
          setMode("login");
          setMessage("Tu sesión expiró. Ingresa nuevamente.");
          setCheckingSession(false);
        }
        return null;
      }
      if (!response.ok) throw new Error(`No pudimos verificar tu sesión (HTTP ${response.status}).`);
      return response.json() as Promise<MeResponse>;
    }).then(me => {
      if (!me || cancelled) return;
      window.location.replace(destinationForRoles(me.roles));
    }).catch(error => {
      if (cancelled) return;
      setMessage(errorMessage(error, "No pudimos verificar tu sesión."));
      setCheckingSession(false);
    });

    return () => {
      cancelled = true;
      document.title = previousTitle;
    };
  }, []);

  const switchMode = (next: AuthMode) => {
    if (busy) return;
    setMode(next);
    setMessage("");
  };

  const submitRegister = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setMessage("");
    setBusy(true);
    const form = new FormData(event.currentTarget);
    const businessName = String(form.get("businessName") || "").trim();

    try {
      const result = await apiRequest<AuthResponse>("/api/v1/auth/register", {
        method: "POST",
        body: JSON.stringify({
          adminName: businessName,
          businessName,
          email: String(form.get("email") || "").trim(),
          password: String(form.get("password") || ""),
          timezone: detectedTimezone(),
          language: detectedLanguage(),
          humanTransferPhone: null
        })
      }, false);
      setAccessToken(result.accessToken);
      window.location.assign("/app");
    } catch (error) {
      setMessage(errorMessage(error, "No fue posible crear la empresa."));
      setBusy(false);
    }
  };

  const submitLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setMessage("");
    setBusy(true);
    const form = new FormData(event.currentTarget);

    try {
      const result = await apiRequest<AuthResponse>("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({
          email: String(form.get("email") || "").trim(),
          password: String(form.get("password") || "")
        })
      }, false);
      setAccessToken(result.accessToken);
      window.location.assign(destinationForRoles(result.user?.roles));
    } catch (error) {
      setMessage(errorMessage(error, "Credenciales inválidas."));
      setBusy(false);
    }
  };

  return (
    <MotionConfig reducedMotion="user">
      <div className={styles.page} data-auth-page="recepvoz">
        <div className={styles.ambient} aria-hidden="true"><i /><i /><i /></div>

        <header className={`${styles.topbar} topbar`}>
          <a className={styles.brandLink} href="/app/sales">
            <BrandMark />
            <span className={styles.brandCopy}><strong>Recepcionista IA</strong><small>para negocios</small></span>
          </a>
          <nav className={styles.publicNav} aria-label="Navegación pública">
            <a href="/app/sales">Cómo funciona</a>
            <a href="/app/pricing">Planes</a>
          </nav>
        </header>

        <main className={styles.main}>
          <section id="authView" className={styles.authGrid}>
            <motion.article
              className={`${styles.hero} rv-auth-hero`}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: .45 }}
            >
              <div className={`${styles.heroCopy} rv-auth-copy`}>
                <span className={styles.eyebrow}>TU NEGOCIO SIEMPRE CONTESTA</span>
                <h1>No pierdas otra llamada.</h1>
                <div className={`${styles.heroEmphasis} rv-hero-emphasis`}>de tu negocio</div>
                <p>RecepVoz atiende consultas y ayuda a gestionar reservas mientras tú sigues trabajando.</p>
                <div className={`${styles.benefits} rv-benefit-row`} aria-label="Beneficios principales">
                  <span><Check size={13} /> Atiende llamadas 24/7</span>
                  <span><Check size={13} /> Agenda citas</span>
                  <span><Check size={13} /> WhatsApp Business</span>
                </div>
                <div className={styles.trustLine}>
                  <ShieldCheck size={15} aria-hidden="true" />
                  <span>Prueba controlada. Configuras el alcance antes de activar.</span>
                </div>
              </div>

              <div className={`${styles.phoneStage} rv-auth-visual rv-phone-stage`} aria-label="RecepVoz atendiendo una llamada">
                <span className={styles.phoneAura} aria-hidden="true" />
                <img className={`${styles.phoneDevice} rv-phone-device`} src="/recepvoz-phone-hero.svg" alt="Teléfono con RecepVoz atendiendo una llamada entrante" width="900" height="1100" />
                <div className={`${styles.phoneBubble} rv-phone-bubble`} data-position="one">✦ Hola, soy tu recepcionista virtual.</div>
                <div className={`${styles.phoneBubble} rv-phone-bubble`} data-position="two">▣ Tengo disponibilidad para mañana.</div>
                <div className={`${styles.phoneBubble} rv-phone-bubble`} data-position="three">✓ Reserva lista para confirmar.</div>
                <div className={`${styles.phoneWave} rv-phone-wave`} aria-hidden="true">
                  {Array.from({ length: 9 }, (_, index) => <i key={index} />)}
                </div>
                <div className={`${styles.phoneStatus} rv-phone-status`}>
                  <i aria-hidden="true" />
                  <span><strong>Recepcionista disponible</strong><small>Atendiendo clientes 24/7</small></span>
                </div>
              </div>
            </motion.article>

            <motion.article className={`${styles.authCard} rv-auth-card`} initial={{ opacity: 0, x: 10 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: .4, delay: .08 }}>
              <div className={styles.cardHead}>
                <span className={styles.cardIcon}><LockKeyhole size={17} /></span>
                <div><strong>{mode === "register" ? "Comienza con RecepVoz" : "Bienvenido de vuelta"}</strong><small>{mode === "register" ? "Crea tu cuenta en menos de un minuto." : "Ingresa a la operación de tu negocio."}</small></div>
              </div>

              <div className={styles.tabs} role="tablist" aria-label="Acceso a RecepVoz">
                <button id="registerTab" role="tab" type="button" aria-selected={mode === "register"} onClick={() => switchMode("register")}>Crear cuenta</button>
                <button id="loginTab" role="tab" type="button" aria-selected={mode === "login"} onClick={() => switchMode("login")}>Ingresar</button>
              </div>

              {checkingSession ? (
                <div className={styles.sessionCheck} role="status"><Sparkles size={18} /><strong>Verificando tu sesión…</strong></div>
              ) : (
                <>
                  <form id="registerForm" className={styles.form} hidden={mode !== "register"} onSubmit={submitRegister} aria-busy={busy}>
                    {mode === "register" && (
                      <>
                        <label htmlFor="registerBusinessName">Nombre del negocio<input id="registerBusinessName" name="businessName" required maxLength={150} autoComplete="organization" placeholder="Restaurante Don Pepe" /></label>
                        <label htmlFor="registerEmail">Correo<input id="registerEmail" name="email" required type="email" maxLength={180} autoComplete="email" placeholder="ana@empresa.cl" /></label>
                        <label htmlFor="registerPassword">Contraseña<input id="registerPassword" name="password" required type="password" minLength={10} maxLength={72} autoComplete="new-password" placeholder="Mínimo 10 caracteres" /></label>
                        <button className={styles.primaryButton} type="submit" disabled={busy}>{busy ? "Creando cuenta…" : "Crear cuenta"}</button>
                        <small className={styles.formHint}>Idioma y zona horaria se detectan automáticamente.</small>
                      </>
                    )}
                  </form>

                  <form id="loginForm" className={styles.form} hidden={mode !== "login"} onSubmit={submitLogin} aria-busy={busy}>
                    {mode === "login" && (
                      <>
                        <label htmlFor="loginEmail">Correo<input id="loginEmail" name="email" required type="email" autoComplete="email" placeholder="ana@empresa.cl" /></label>
                        <label htmlFor="loginPassword">Contraseña<input id="loginPassword" name="password" required type="password" autoComplete="current-password" /></label>
                        <button className={styles.primaryButton} type="submit" disabled={busy}>{busy ? "Ingresando…" : "Ingresar a RecepVoz"}</button>
                      </>
                    )}
                  </form>
                </>
              )}

              <div id="authMessage" className={styles.message} role={message ? "alert" : undefined} hidden={!message}>{message}</div>
              <p className={styles.legal}>Al crear una cuenta aceptas los <a href="/terms.html">Términos</a> y la <a href="/privacy.html">Política de privacidad</a>.</p>
              <a className={styles.secondaryLink} href="/app/sales">Ver cómo funciona <ArrowRight size={14} /></a>
            </motion.article>
          </section>

          <section className={`${styles.featureStrip} rv-auth-feature-strip`} aria-label="Capacidades principales de RecepVoz">
            {features.map(feature => <ProductFeature key={feature.feature} {...feature} />)}
          </section>
        </main>
      </div>
    </MotionConfig>
  );
}
