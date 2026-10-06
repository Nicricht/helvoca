import { useEffect } from "react";
import { MotionConfig, motion } from "framer-motion";
import {
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  Clock3,
  MessageCircle,
  PackageCheck,
  PhoneCall,
  ShieldCheck,
  ShoppingBag,
  Sparkles,
  Workflow,
  Zap
} from "lucide-react";
import styles from "./SalesPage.module.css";

const workCards = [
  {
    key: "agenda",
    eyebrow: "AGENDA",
    title: "Convierte una conversación en una reserva.",
    copy: "Consulta disponibilidad, respeta tus horarios y deja el siguiente paso registrado para tu equipo.",
    image: "/app/assets/home/agenda.webp",
    alt: "Vista de agenda y reservas de RecepVoz",
    icon: CalendarDays
  },
  {
    key: "orders",
    eyebrow: "PEDIDOS",
    title: "Toma pedidos sin convertir el teléfono en un cuello de botella.",
    copy: "Trabaja con el catálogo configurado, confirma lo necesario y deja el pedido listo para continuar.",
    image: "/app/assets/home/orders.webp",
    alt: "Vista de pedidos gestionados por RecepVoz",
    icon: ShoppingBag
  },
  {
    key: "inventory",
    eyebrow: "INVENTARIO",
    title: "Responde con información que viene de tu operación.",
    copy: "El stock y los productos configurados ayudan a evitar respuestas desconectadas de la realidad del negocio.",
    image: "/app/assets/home/inventory.webp",
    alt: "Vista de inventario y stock de RecepVoz",
    icon: PackageCheck
  },
  {
    key: "automation",
    eyebrow: "AUTOMATIZACIÓN",
    title: "Deja que la IA avance solo hasta donde tú permitas.",
    copy: "Define capacidades y reglas antes del piloto. La conversación puede informar, reservar, cotizar o registrar una solicitud según tu configuración.",
    image: "/app/assets/home/automation.webp",
    alt: "Vista de automatización y reglas de RecepVoz",
    icon: Workflow
  }
] as const;

const steps = [
  {
    number: "01",
    title: "Configuramos tu negocio",
    copy: "Servicios, precios, horarios, catálogo, conocimiento y la información que la IA puede usar."
  },
  {
    number: "02",
    title: "Definimos qué puede hacer",
    copy: "Reservar, tomar pedidos, cotizar, registrar solicitudes u otras capacidades habilitadas."
  },
  {
    number: "03",
    title: "Probamos antes de activar",
    copy: "Simulamos conversaciones y revisamos el comportamiento con tus propias reglas."
  },
  {
    number: "04",
    title: "Activamos un piloto controlado",
    copy: "Partimos con un alcance claro, medible y ajustable antes de escalar."
  }
] as const;

const businessGroups = [
  {
    eyebrow: "NEGOCIOS CON AGENDA",
    title: "Cuando cada llamada puede terminar en una hora reservada.",
    examples: "Clínicas, estética, veterinarias, peluquerías, academias y servicios profesionales.",
    copy: "RecepVoz puede responder consultas, revisar disponibilidad y gestionar reservas cuando esa capacidad está habilitada.",
    icon: CalendarDays
  },
  {
    eyebrow: "VENTAS Y SOLICITUDES",
    title: "Cuando responder rápido ayuda a que el cliente siga avanzando.",
    examples: "Restaurantes, talleres, inmobiliarias, comercios, delivery y servicios.",
    copy: "Puede trabajar con catálogo, pedidos, cotizaciones, leads o solicitudes según lo que cada empresa configure.",
    icon: ShoppingBag
  }
] as const;

function setMetaDescription(content: string) {
  let meta = document.querySelector('meta[name="description"]') as HTMLMetaElement | null;
  if (!meta) {
    meta = document.createElement("meta");
    meta.name = "description";
    document.head.appendChild(meta);
  }
  meta.content = content;
}

export function SalesPage() {
  useEffect(() => {
    const previousTitle = document.title;
    const previousDescription =
      (document.querySelector('meta[name="description"]') as HTMLMetaElement | null)?.content || "";

    document.title = "RecepVoz · Atención con IA para llamadas y WhatsApp";
    setMetaDescription(
      "RecepVoz ayuda a negocios a responder llamadas y WhatsApp, usar información real de la empresa y convertir conversaciones en reservas, pedidos, cotizaciones o solicitudes."
    );

    return () => {
      document.title = previousTitle;
      setMetaDescription(previousDescription);
    };
  }, []);

  return (
    <MotionConfig reducedMotion="user">
      <div className={styles.page} data-public-sales="true">
        <div className={styles.ambient} aria-hidden="true">
          <span className={styles.ambientOrb} />
          <span className={styles.ambientLine} />
          <span className={styles.ambientLineTwo} />
          <span className={styles.ambientGrid} />
        </div>

        <header className={styles.header}>
          <a className={styles.brand} href="/app/sales" aria-label="RecepVoz, inicio comercial">
            <span className={styles.brandMark}><Sparkles size={18} aria-hidden="true" /></span>
            <span>
              <strong>RecepVoz</strong>
              <small>Recepcionista IA</small>
            </span>
          </a>

          <nav className={styles.nav} aria-label="Navegación comercial">
            <a href="#como-funciona">Cómo funciona</a>
            <a href="#producto">Qué hace</a>
            <a href="#negocios">Para quién sirve</a>
            <a href="/app/pricing">Planes</a>
          </nav>

          <a className={styles.headerCta} href="/">
            Probar RecepVoz <ArrowRight size={14} aria-hidden="true" />
          </a>
        </header>

        <main>
          <section className={styles.hero}>
            <motion.div
              className={styles.heroCopy}
              initial={{ opacity: 0, y: 18 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: .55, ease: [0.16, 1, 0.3, 1] }}
            >
              <span className={styles.pill}>
                <i aria-hidden="true" />
                ATENCIÓN CON IA PARA NEGOCIOS
              </span>

              <h1>
                Que una llamada o un WhatsApp sin responder{" "}
                <span>no se convierta en un cliente perdido.</span>
              </h1>

              <p className={styles.heroLead}>
                RecepVoz atiende consultas usando la información real de tu negocio y puede convertir
                una conversación en una reserva, pedido, cotización, solicitud o siguiente paso concreto.
              </p>

              <div className={styles.heroActions}>
                <a className={styles.primaryButton} href="/">
                  Probar con mi negocio <ArrowRight size={16} aria-hidden="true" />
                </a>
                <a className={styles.secondaryButton} href="/app/pricing">
                  Planes desde $24.990
                </a>
              </div>

              <div className={styles.heroTrust}>
                <span><ShieldCheck size={14} aria-hidden="true" /> Configuración asistida</span>
                <span><CheckCircle2 size={14} aria-hidden="true" /> Piloto controlado</span>
                <span><Zap size={14} aria-hidden="true" /> Acciones según tus reglas</span>
              </div>

              <p className={styles.heroFine}>
                WhatsApp se habilita de forma controlada según la configuración de cada negocio.
              </p>
            </motion.div>

            <motion.div
              className={styles.heroStage}
              initial={{ opacity: 0, scale: .97, y: 12 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              transition={{ duration: .65, delay: .1, ease: [0.16, 1, 0.3, 1] }}
              aria-label="Vista ilustrativa de RecepVoz atendiendo"
            >
              <span className={styles.stageGlow} aria-hidden="true" />
              <span className={styles.stageOrbit} aria-hidden="true" />
              <img
                className={styles.heroBot}
                src="/app/assets/home/hero-bot.webp"
                alt="Asistente visual de RecepVoz"
              />

              <div className={styles.liveCard}>
                <div className={styles.liveTop}>
                  <span><i aria-hidden="true" /> Atendiendo ahora</span>
                  <small>Vista ilustrativa</small>
                </div>

                <div className={styles.callRow}>
                  <span className={styles.channelIcon}><PhoneCall size={17} aria-hidden="true" /></span>
                  <div>
                    <strong>Llamada entrante</strong>
                    <span>Cliente consulta disponibilidad</span>
                  </div>
                  <div className={styles.waveform} aria-hidden="true">
                    <i /><i /><i /><i /><i /><i /><i />
                  </div>
                </div>

                <div className={styles.timeline}>
                  <article>
                    <span className={styles.timelineDot} data-tone="cyan" />
                    <div><small>Comprende</small><strong>“Quiero una hora para mañana”</strong></div>
                  </article>
                  <article>
                    <span className={styles.timelineDot} data-tone="violet" />
                    <div><small>Consulta</small><strong>Horario y disponibilidad configurados</strong></div>
                  </article>
                  <article>
                    <span className={styles.timelineDot} data-tone="green" />
                    <div><small>Avanza</small><strong>Reserva preparada para confirmar</strong></div>
                  </article>
                </div>
              </div>

              <div className={styles.messageChip}>
                <MessageCircle size={15} aria-hidden="true" />
                <span><strong>WhatsApp</strong><small>Misma información del negocio</small></span>
              </div>

              <div className={styles.statusChip}>
                <CheckCircle2 size={15} aria-hidden="true" />
                <span><strong>Negocio conectado</strong><small>Servicios · horarios · conocimiento</small></span>
              </div>
            </motion.div>
          </section>

          <section className={styles.truthStrip} aria-label="Qué conecta RecepVoz">
            <div><PhoneCall size={17} aria-hidden="true" /><span><strong>Teléfono</strong><small>Consulta y acción</small></span></div>
            <i aria-hidden="true" />
            <div><MessageCircle size={17} aria-hidden="true" /><span><strong>WhatsApp</strong><small>Misma lógica de negocio</small></span></div>
            <i aria-hidden="true" />
            <div><Workflow size={17} aria-hidden="true" /><span><strong>Operación</strong><small>Agenda · pedidos · solicitudes</small></span></div>
            <i aria-hidden="true" />
            <div><ShieldCheck size={17} aria-hidden="true" /><span><strong>Reglas</strong><small>Solo acciones habilitadas</small></span></div>
          </section>

          <section id="producto" className={styles.workSection}>
            <div className={styles.sectionIntro}>
              <span className={styles.eyebrow}>TRABAJO VISIBLE</span>
              <h2>RecepVoz trabaja mientras tu equipo sigue con el negocio.</h2>
              <p>
                No es una pantalla decorativa alrededor de un chatbot. La conversación puede consultar
                información configurada y avanzar hacia una acción concreta cuando está permitida.
              </p>
            </div>

            <div className={styles.workGrid}>
              {workCards.map((card, index) => {
                const Icon = card.icon;
                return (
                  <motion.article
                    key={card.key}
                    className={styles.workCard}
                    initial={{ opacity: 0, y: 18 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true, amount: .18 }}
                    transition={{ duration: .42, delay: index * .06, ease: [0.16, 1, 0.3, 1] }}
                  >
                    <div className={styles.workVisual}>
                      <img src={card.image} alt={card.alt} />
                      <span className={styles.visualIcon}><Icon size={17} aria-hidden="true" /></span>
                      <span className={styles.visualScan} aria-hidden="true" />
                    </div>
                    <div className={styles.workCopy}>
                      <span className={styles.cardEyebrow}>{card.eyebrow}</span>
                      <h3>{card.title}</h3>
                      <p>{card.copy}</p>
                    </div>
                  </motion.article>
                );
              })}
            </div>
          </section>

          <section id="como-funciona" className={styles.stepsSection}>
            <div className={styles.stepsHeading}>
              <div>
                <span className={styles.eyebrow}>DE CERO A PILOTO</span>
                <h2>Cómo empieza</h2>
              </div>
              <p>
                Configuramos primero, probamos después y activamos cuando las reglas están claras.
              </p>
            </div>

            <div className={styles.stepsGrid}>
              {steps.map((step, index) => (
                <motion.article
                  key={step.number}
                  className={styles.stepCard}
                  initial={{ opacity: 0, y: 14 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true, amount: .25 }}
                  transition={{ duration: .36, delay: index * .05 }}
                >
                  <span className={styles.stepNumber}>{step.number}</span>
                  <span className={styles.stepLine} aria-hidden="true" />
                  <h3>{step.title}</h3>
                  <p>{step.copy}</p>
                </motion.article>
              ))}
            </div>
          </section>

          <section id="negocios" className={styles.businessSection}>
            <div className={styles.sectionIntro}>
              <span className={styles.eyebrow}>UN MOTOR, DISTINTOS NEGOCIOS</span>
              <h2>La experiencia cambia según lo que tu negocio realmente necesita hacer.</h2>
              <p>
                La misma plataforma puede orientarse a agenda, ventas o solicitudes sin inventar un flujo
                que no corresponde a tu operación.
              </p>
            </div>

            <div className={styles.businessGrid}>
              {businessGroups.map(group => {
                const Icon = group.icon;
                return (
                  <article key={group.eyebrow} className={styles.businessCard}>
                    <span className={styles.businessIcon}><Icon size={21} aria-hidden="true" /></span>
                    <span className={styles.cardEyebrow}>{group.eyebrow}</span>
                    <h3>{group.title}</h3>
                    <strong>{group.examples}</strong>
                    <p>{group.copy}</p>
                  </article>
                );
              })}
            </div>
          </section>

          <section className={styles.valueSection}>
            <div className={styles.valueOrb} aria-hidden="true" />
            <div className={styles.valueCopy}>
              <span className={styles.eyebrow}>EL VALOR</span>
              <h2>Responder es el comienzo. Lo importante es que la conversación avance.</h2>
              <p>
                RecepVoz conecta la atención con información y acciones del negocio para reducir el salto
                entre “alguien preguntó” y “hay un siguiente paso registrado”.
              </p>
            </div>

            <div className={styles.valueFlow} aria-label="Flujo de valor de RecepVoz">
              <span>Cliente consulta</span>
              <ArrowRight size={15} aria-hidden="true" />
              <span>RecepVoz entiende</span>
              <ArrowRight size={15} aria-hidden="true" />
              <span>Consulta datos reales</span>
              <ArrowRight size={15} aria-hidden="true" />
              <span>Responde</span>
              <ArrowRight size={15} aria-hidden="true" />
              <span>Ejecuta una acción permitida</span>
            </div>
          </section>

          <section className={styles.finalCta}>
            <div className={styles.finalCopy}>
              <span className={styles.eyebrow}>PILOTO COMERCIAL</span>
              <h2>Pruébalo con tu propio negocio.</h2>
              <p>
                La configuración inicial está incluida durante el lanzamiento. Partimos desde $24.990 CLP
                al mes y definimos el alcance antes de activar.
              </p>
              <div className={styles.finalProof}>
                <span><Clock3 size={14} aria-hidden="true" /> Configuración guiada</span>
                <span><ShieldCheck size={14} aria-hidden="true" /> Alcance definido antes de activar</span>
              </div>
            </div>

            <div className={styles.finalActions}>
              <a className={styles.primaryButton} href="/">
                Configurar mi prueba <ArrowRight size={16} aria-hidden="true" />
              </a>
              <a className={styles.secondaryButton} href="/app/pricing">Ver planes</a>
            </div>
          </section>
        </main>

        <footer className={styles.footer}>
          <div className={styles.footerBrand}>
            <span className={styles.brandMark}><Sparkles size={16} aria-hidden="true" /></span>
            <span><strong>RecepVoz</strong><small>Atención digital con IA para empresas.</small></span>
          </div>
          <nav aria-label="Información pública">
            <a href="/app/pricing">Planes</a>
            <a href="/privacy.html">Privacidad</a>
            <a href="/terms.html">Términos</a>
          </nav>
        </footer>
      </div>
    </MotionConfig>
  );
}
