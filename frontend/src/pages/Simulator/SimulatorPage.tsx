import {
  FormEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState
} from "react";
import { motion, useReducedMotion } from "framer-motion";
import {
  Bot,
  Check,
  CircleStop,
  FlaskConical,
  History,
  Mic,
  Play,
  Send,
  ShieldCheck,
  Sparkles,
  Volume2
} from "lucide-react";
import { AppShell } from "../../components/AppShell/AppShell";
import {
  finishSimulatorSession,
  getSimulatorBusiness,
  getSimulatorTrace,
  sendSimulatorMessage,
  startSimulatorSession,
  type SimulatorTraceAction
} from "../../features/simulator/api";
import styles from "./SimulatorPage.module.css";

type Operation = "identity" | "start" | "send" | "finish" | null;
type ChatRole = "user" | "assistant";

interface ChatMessage {
  id: string;
  role: ChatRole;
  text: string;
}

interface SpeechRecognitionEventLike {
  results?: ArrayLike<ArrayLike<{ transcript?: string }>>;
}

interface SpeechRecognitionErrorEventLike {
  error?: string;
}

interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  onstart: (() => void) | null;
  onend: (() => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  start(): void;
  stop(): void;
}

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

const ACTION_LABELS: Record<string, string> = {
  REQUEST_CREATED: "Solicitud creada",
  CREATE_REQUEST: "Solicitud creada",
  BOOKING_CREATED: "Reserva creada",
  CREATE_BOOKING: "Reserva creada",
  BOOKING_RESCHEDULED: "Reserva reprogramada",
  RESCHEDULE_BOOKING: "Reserva reprogramada",
  BOOKING_CANCELLED: "Reserva cancelada",
  CANCEL_BOOKING: "Reserva cancelada",
  CUSTOMER_REGISTERED: "Cliente registrado",
  REGISTER_CALLER: "Cliente registrado",
  CALLER_LOOKUP: "Cliente identificado",
  FIND_CALLER: "Cliente identificado",
  KNOWLEDGE_SEARCH: "Consultó información",
  SEARCH_KNOWLEDGE: "Consultó información",
  SERVICES_LISTED: "Consultó servicios",
  LIST_SERVICES: "Consultó servicios",
  AVAILABILITY_LISTED: "Consultó horarios disponibles",
  LIST_AVAILABLE_SLOTS: "Consultó horarios disponibles",
  AVAILABILITY_CHECKED: "Verificó disponibilidad",
  CHECK_BOOKING_AVAILABILITY: "Verificó disponibilidad",
  HUMAN_TRANSFER: "Derivación a una persona",
  TRANSFER_TO_HUMAN: "Derivación a una persona",
  UNANSWERED_QUESTION_RECORDED: "Pregunta pendiente para revisar"
};

const RESOLUTION_LABELS: Record<string, string> = {
  BOOKING_CREATED: "Reserva creada",
  REQUEST_CREATED: "Solicitud creada",
  HUMAN_TRANSFER: "Derivado a una persona",
  TRANSFER_TO_HUMAN: "Derivado a una persona",
  UNANSWERED_QUESTION_RECORDED: "Pregunta pendiente para revisar",
  NO_ACTION: "Sin acción"
};

function readableCode(value?: string | null, labels: Record<string, string> = {}) {
  if (!value) return "";
  if (labels[value]) return labels[value];
  if (!/^[A-Z0-9_]+$/.test(value)) return value;
  return value
    .toLowerCase()
    .split("_")
    .filter(Boolean)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function actionLabel(value?: string | null) {
  return readableCode(value, ACTION_LABELS) || "Acción simulada";
}

function resolutionLabel(value?: string | null) {
  return readableCode(value, RESOLUTION_LABELS) || "Sin acción";
}

function recognitionConstructor(): SpeechRecognitionConstructor | null {
  const speechWindow = window as Window & {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
  };
  return speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition ?? null;
}

function speak(text: string) {
  if (!text || !("speechSynthesis" in window)) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = "es-CL";
  utterance.rate = 1;
  window.speechSynthesis.speak(utterance);
}

function traceTone(action: SimulatorTraceAction) {
  return action.success === false ? "danger" : "success";
}

export function SimulatorPage() {
  const reduceMotion = useReducedMotion();
  const [businessName, setBusinessName] = useState("Tu negocio");
  const [identityReady, setIdentityReady] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [active, setActive] = useState(false);
  const [operation, setOperation] = useState<Operation>("identity");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [resolution, setResolution] = useState("Sin acción");
  const [actions, setActions] = useState<SimulatorTraceAction[]>([]);
  const [alert, setAlert] = useState("");
  const [recognitionSupported, setRecognitionSupported] = useState(false);
  const [listening, setListening] = useState(false);
  const [voiceHint, setVoiceHint] = useState(
    "Si tu navegador admite reconocimiento de voz, puedes hablar y la recepcionista leerá sus respuestas en voz alta."
  );

  const sessionRef = useRef<string | null>(null);
  const activeRef = useRef(false);
  const operationRef = useRef<Operation>("identity");
  const listeningRef = useRef(false);
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const recognitionSessionRef = useRef<string | null>(null);
  const alertTimerRef = useRef<number | null>(null);
  const messageCounterRef = useRef(0);
  const sendMessageRef = useRef<(value: string) => Promise<void>>(async () => {});

  const operationBusy = operation !== null;
  const sessionBusy = operationBusy || listening;
  const sessionState = active
    ? "Prueba activa"
    : sessionId
      ? "Prueba finalizada"
      : "Sin prueba activa";

  const feedback = useMemo(() => {
    if (listening) return "Escuchando tu mensaje…";
    if (operation === "identity") return "Preparando tu simulador…";
    if (operation === "start") return "Iniciando prueba segura…";
    if (operation === "send") return "RecepVoz está respondiendo…";
    if (operation === "finish") return "Finalizando prueba segura…";
    return "";
  }, [listening, operation]);

  const showAlert = useCallback((message: string) => {
    setAlert(message);
    if (alertTimerRef.current) window.clearTimeout(alertTimerRef.current);
    alertTimerRef.current = window.setTimeout(() => setAlert(""), 3500);
  }, []);

  const setSessionActive = useCallback((value: boolean) => {
    activeRef.current = value;
    setActive(value);
  }, []);

  const beginOperation = useCallback((next: Exclude<Operation, null>) => {
    if (operationRef.current || listeningRef.current) return false;
    operationRef.current = next;
    setOperation(next);
    return true;
  }, []);

  const endOperation = useCallback(() => {
    operationRef.current = null;
    setOperation(null);
  }, []);

  const resetTrace = useCallback(() => {
    setResolution("Sin acción");
    setActions([]);
  }, []);

  const loadTrace = useCallback(async (targetSessionId: string) => {
    try {
      const detail = await getSimulatorTrace(targetSessionId);
      if (targetSessionId !== sessionRef.current) return;
      setResolution(resolutionLabel(detail.call?.resolution));
      setActions([...(detail.actions ?? [])].reverse());
    } catch (error) {
      showAlert(error instanceof Error ? error.message : "No pudimos cargar la traza de la prueba.");
    }
  }, [showAlert]);

  const finishCurrent = useCallback(async (silent = false, nested = false) => {
    const targetSessionId = sessionRef.current;
    if (!targetSessionId || !activeRef.current) return true;
    if (!nested && (operationRef.current || listeningRef.current)) return false;

    if (!nested) {
      operationRef.current = "finish";
      setOperation("finish");
    }

    try {
      await finishSimulatorSession(targetSessionId);
      setSessionActive(false);
      await loadTrace(targetSessionId);
      if (!silent) {
        showAlert("Prueba finalizada. Ningún dato comercial real fue modificado.");
      }
      return true;
    } catch (error) {
      if (!silent) {
        showAlert(error instanceof Error ? error.message : "No pudimos finalizar la prueba.");
      }
      return false;
    } finally {
      if (!nested) endOperation();
    }
  }, [endOperation, loadTrace, setSessionActive, showAlert]);

  const startSession = useCallback(async () => {
    if (!beginOperation("start")) return;
    try {
      if (activeRef.current) {
        const closed = await finishCurrent(true, true);
        if (!closed) {
          showAlert("No pude finalizar la prueba actual. Intenta nuevamente.");
          return;
        }
      }

      const result = await startSimulatorSession();
      sessionRef.current = result.sessionId;
      setSessionId(result.sessionId);
      setMessages([]);
      setInput("");
      resetTrace();
      setSessionActive(true);
      setMessages([{
        id: `assistant-${++messageCounterRef.current}`,
        role: "assistant",
        text: result.greeting
      }]);
      speak(result.greeting);
      await loadTrace(result.sessionId);
    } catch (error) {
      showAlert(error instanceof Error ? error.message : "No pudimos iniciar la prueba.");
    } finally {
      endOperation();
    }
  }, [beginOperation, endOperation, finishCurrent, loadTrace, resetTrace, setSessionActive, showAlert]);

  const sendMessage = useCallback(async (value: string) => {
    const targetSessionId = sessionRef.current;
    const clean = value.trim();
    if (!activeRef.current || !targetSessionId || !clean || operationRef.current) return;

    operationRef.current = "send";
    setOperation("send");

    const userMessage: ChatMessage = {
      id: `user-${++messageCounterRef.current}`,
      role: "user",
      text: clean
    };
    setMessages(current => [...current, userMessage]);
    setInput("");

    try {
      const result = await sendSimulatorMessage(targetSessionId, clean);
      if (targetSessionId !== sessionRef.current) return;

      setMessages(current => [...current, {
        id: `assistant-${++messageCounterRef.current}`,
        role: "assistant",
        text: result.reply
      }]);
      speak(result.reply);
      await loadTrace(targetSessionId);
      if (result.ended) setSessionActive(false);
    } catch (error) {
      setMessages(current => current.filter(message => message.id !== userMessage.id));
      setInput(clean);
      showAlert(error instanceof Error ? error.message : "No pudimos enviar el mensaje.");
    } finally {
      endOperation();
    }
  }, [endOperation, loadTrace, setSessionActive, showAlert]);

  sendMessageRef.current = sendMessage;

  useEffect(() => {
    let cancelled = false;
    operationRef.current = "identity";
    setOperation("identity");

    getSimulatorBusiness()
      .then(business => {
        if (cancelled) return;
        setBusinessName(business.name?.trim() || "Tu negocio");
      })
      .catch(() => {
        if (!cancelled) setBusinessName("Tu negocio");
      })
      .finally(() => {
        if (cancelled) return;
        setIdentityReady(true);
        operationRef.current = null;
        setOperation(null);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const Recognition = recognitionConstructor();
    if (!Recognition) {
      setRecognitionSupported(false);
      setVoiceHint("Tu navegador no ofrece reconocimiento de voz. Puedes usar el chat igualmente.");
      return;
    }

    const recognition = new Recognition();
    recognition.lang = "es-CL";
    recognition.interimResults = false;
    recognition.continuous = false;
    recognitionRef.current = recognition;
    setRecognitionSupported(true);
    setVoiceHint("Puedes escribir o hablar. Las respuestas se leen en voz alta desde el navegador.");

    recognition.onstart = () => {
      listeningRef.current = true;
      setListening(true);
      recognitionSessionRef.current = sessionRef.current;
      setVoiceHint("Escuchando… habla como si estuvieras llamando al negocio.");
    };

    recognition.onend = () => {
      listeningRef.current = false;
      setListening(false);
      recognitionSessionRef.current = null;
      setVoiceHint("Puedes escribir o hablar. Las respuestas se leen en voz alta desde el navegador.");
    };

    recognition.onerror = event => {
      listeningRef.current = false;
      setListening(false);
      recognitionSessionRef.current = null;
      setVoiceHint("Puedes escribir o hablar. Las respuestas se leen en voz alta desde el navegador.");
      showAlert(`Micrófono: ${event.error || "no disponible"}`);
    };

    recognition.onresult = event => {
      const resultSessionId = recognitionSessionRef.current;
      const text = event.results?.[0]?.[0]?.transcript?.trim() || "";
      listeningRef.current = false;
      setListening(false);
      recognitionSessionRef.current = null;
      if (!activeRef.current || resultSessionId !== sessionRef.current || !text) return;
      setInput(text);
      void sendMessageRef.current(text);
    };

    return () => {
      recognitionRef.current = null;
      if ("speechSynthesis" in window) window.speechSynthesis.cancel();
    };
  }, [showAlert]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void sendMessage(input);
  }

  function toggleRecognition() {
    const recognition = recognitionRef.current;
    if (!recognition || !activeRef.current || operationRef.current) return;

    try {
      if (listeningRef.current) {
        recognition.stop();
        return;
      }

      listeningRef.current = true;
      setListening(true);
      recognitionSessionRef.current = sessionRef.current;
      setVoiceHint("Activando micrófono…");
      recognition.start();
    } catch (error) {
      listeningRef.current = false;
      setListening(false);
      recognitionSessionRef.current = null;
      showAlert(error instanceof Error ? error.message : "Micrófono no disponible.");
    }
  }

  const micTitle = !recognitionSupported
    ? "El reconocimiento de voz no está disponible en este navegador."
    : !active
      ? "Inicia una prueba para poder usar el micrófono."
      : operationBusy
        ? "Espera a que termine la operación actual."
        : listening
          ? "Detener escucha."
          : "Hablar.";

  return (
    <AppShell>
      <main className={`rv-page-frame ${styles.page}`} data-visual-page="simulator">
        <div className={styles.atmosphere} aria-hidden="true">
          <span className={styles.orbA} />
          <span className={styles.orbB} />
          <span className={styles.grid} />
        </div>

        <header className={styles.header}>
          <div>
            <p className="eyebrow">LABORATORIO SEGURO</p>
            <h1>Simulador</h1>
            <p>Prueba cómo atendería RecepVoz antes de exponerlo a una conversación real.</p>
          </div>
          <div className={styles.headerMeta}>
            <span className={styles.businessChip}>{businessName}</span>
            <span
              className={styles.sessionBadge}
              data-active={active ? "true" : undefined}
              data-testid="simulator-session-state"
            >
              {sessionState}
            </span>
          </div>
        </header>

        <motion.section
          className={styles.safeCard}
          initial={reduceMotion ? false : { opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: reduceMotion ? 0 : .28 }}
          aria-label="Modo seguro"
        >
          <span className={styles.safeIcon} aria-hidden="true"><ShieldCheck size={22} /></span>
          <div>
            <strong>Modo seguro</strong>
            <span>
              No crea datos comerciales reales ni realiza llamadas telefónicas. Tampoco envía WhatsApp real
              ni modifica clientes, reservas o pedidos reales.
            </span>
          </div>
          <span className={styles.safePill}><Check size={13} aria-hidden="true" /> Aislado</span>
        </motion.section>

        <div className={styles.workspace}>
          <motion.section
            className={styles.chatPanel}
            initial={reduceMotion ? false : { opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: reduceMotion ? 0 : .3, delay: reduceMotion ? 0 : .04 }}
            aria-labelledby="simulatorChatTitle"
          >
            <div className={styles.panelHeader}>
              <div>
                <span className={styles.panelEyebrow}><Sparkles size={13} aria-hidden="true" /> CONVERSACIÓN</span>
                <h2 id="simulatorChatTitle">Prueba tu recepcionista</h2>
              </div>
              <div className={styles.actions}>
                <a className="button ghost" href="/app/agenda">
                  <History size={15} aria-hidden="true" /> Agenda
                </a>
                <button
                  className="button primary"
                  type="button"
                  onClick={() => void startSession()}
                  disabled={!identityReady || sessionBusy}
                  title={sessionBusy ? "Espera a que termine la operación actual." : "Iniciar una prueba segura nueva."}
                >
                  <Play size={15} aria-hidden="true" /> Nueva prueba
                </button>
                <button
                  className="button ghost"
                  type="button"
                  onClick={() => void finishCurrent(false)}
                  disabled={!active || sessionBusy}
                  title={!active ? "Inicia una prueba para poder finalizarla." : sessionBusy ? "Espera a que termine la operación actual." : "Finalizar esta prueba segura."}
                >
                  <CircleStop size={15} aria-hidden="true" /> Finalizar
                </button>
              </div>
            </div>

            {feedback && (
              <div className={styles.feedback} role="status" aria-live="polite">
                <span className={styles.pulse} aria-hidden="true" />
                {feedback}
              </div>
            )}

            <div className={styles.chat} aria-live="polite" aria-busy={sessionBusy ? "true" : "false"}>
              {messages.length === 0 ? (
                <div className={styles.emptyChat}>
                  <span aria-hidden="true"><Bot size={28} /></span>
                  <strong>Inicia una prueba para comenzar</strong>
                  <p>Escribe o habla como lo haría un cliente. Todo ocurre dentro del entorno de simulación.</p>
                </div>
              ) : messages.map(message => (
                <article key={message.id} className={message.role === "user" ? styles.userBubble : styles.aiBubble}>
                  <p>{message.text}</p>
                  <small>{message.role === "user" ? "Tú" : businessName}</small>
                </article>
              ))}
            </div>

            <form className={styles.composer} onSubmit={submit}>
              <button
                className={styles.micButton}
                type="button"
                aria-label="Usar micrófono"
                aria-pressed={listening}
                disabled={!active || operationBusy || !recognitionSupported}
                title={micTitle}
                data-listening={listening ? "true" : undefined}
                onClick={toggleRecognition}
              >
                <Mic size={18} aria-hidden="true" />
              </button>
              <input
                value={input}
                onChange={event => setInput(event.target.value)}
                aria-label="Mensaje de prueba"
                autoComplete="off"
                maxLength={1000}
                placeholder="Escribe o usa el micrófono…"
                disabled={!active || sessionBusy}
                title={!active ? "Inicia una prueba para poder escribir." : sessionBusy ? "Espera a que termine la operación actual." : "Escribe tu mensaje de prueba."}
              />
              <button
                className="button primary"
                type="submit"
                disabled={!active || sessionBusy || !input.trim()}
                title={!active ? "Inicia una prueba para poder enviar mensajes." : sessionBusy ? "Espera a que termine la operación actual." : "Enviar mensaje de prueba."}
              >
                <Send size={15} aria-hidden="true" /> Enviar
              </button>
            </form>

            <p className={styles.voiceHint} data-testid="simulator-voice-hint">
              <Volume2 size={14} aria-hidden="true" />
              <span>{voiceHint}</span>
            </p>
          </motion.section>

          <motion.aside
            className={styles.tracePanel}
            initial={reduceMotion ? false : { opacity: 0, x: 10 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: reduceMotion ? 0 : .3, delay: reduceMotion ? 0 : .08 }}
            aria-labelledby="simulatorTraceTitle"
          >
            <div className={styles.panelHeader}>
              <div>
                <span className={styles.panelEyebrow}><FlaskConical size={13} aria-hidden="true" /> RESULTADO</span>
                <h2 id="simulatorTraceTitle">Qué haría RecepVoz</h2>
              </div>
            </div>

            <div className={styles.traceStats}>
              <article>
                <span>Resultado actual</span>
                <strong data-testid="simulator-resolution">{resolution}</strong>
              </article>
              <article>
                <span>Acciones verificadas</span>
                <strong data-testid="simulator-action-count">{actions.length}</strong>
              </article>
            </div>

            <div className={styles.traceList} data-testid="simulator-trace">
              {actions.length === 0 ? (
                <div className={styles.emptyTrace}>
                  <FlaskConical size={22} aria-hidden="true" />
                  <span>Las acciones simuladas aparecerán aquí en lenguaje claro.</span>
                </div>
              ) : actions.map(action => (
                <article key={action.id} className={styles.traceItem} data-tone={traceTone(action)}>
                  <div>
                    <strong>{actionLabel(action.actionType)}</strong>
                    <span>{action.success === false ? "Falló" : "Confirmada"}</span>
                  </div>
                  {action.detail && <p>{action.detail}</p>}
                  {action.errorCode && <small>{readableCode(action.errorCode)}</small>}
                </article>
              ))}
            </div>
          </motion.aside>
        </div>

        {alert && (
          <div className={styles.toast} role="alert" aria-live="assertive">
            {alert}
          </div>
        )}
      </main>
    </AppShell>
  );
}
