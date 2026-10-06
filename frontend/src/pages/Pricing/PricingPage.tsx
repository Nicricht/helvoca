import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { MotionConfig, motion } from "framer-motion";
import { ArrowRight, Check, Headphones, MessageCircle, PhoneCall, ShieldCheck, Sparkles, Users } from "lucide-react";
import styles from "./PricingPage.module.css";

type PlanOffer = {
  code: string;
  name: string;
  monthlyPriceClp: number;
  includedMinutes: number;
  maxConcurrentCalls: number;
  overagePerMinuteClp: number | null;
  customPricing: boolean;
  recommended: boolean;
};

async function fetchPlans(): Promise<PlanOffer[]> {
  const response = await fetch("/api/v1/public/pricing", { method: "GET", headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error(`Pricing request failed with HTTP ${response.status}`);
  return response.json() as Promise<PlanOffer[]>;
}

function formatClp(value: number) {
  return new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 }).format(value);
}

function setMetaDescription(content: string) {
  let meta = document.querySelector('meta[name="description"]') as HTMLMetaElement | null;
  if (!meta) {
    meta = document.createElement("meta");
    meta.name = "description";
    document.head.appendChild(meta);
  }
  meta.content = content;
}

function PlanCard({ plan, index }: { plan: PlanOffer; index: number }) {
  const overage = plan.customPricing
    ? "Excedentes según cotización"
    : plan.overagePerMinuteClp == null
      ? "Excedentes según condiciones del plan"
      : `${formatClp(plan.overagePerMinuteClp)} por minuto adicional`;
  const price = plan.customPricing ? `Desde ${formatClp(plan.monthlyPriceClp)}` : formatClp(plan.monthlyPriceClp);

  return (
    <motion.article
      className={styles.planCard}
      data-recommended={plan.recommended ? "true" : "false"}
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: .42, delay: index * .06, ease: [0.16, 1, 0.3, 1] }}
    >
      {plan.recommended && <span className={styles.recommended}>RECOMENDADO</span>}
      <div className={styles.planTop}>
        <span className={styles.planCode}>{plan.code}</span>
        <h2>{plan.name}</h2>
        <div className={styles.price}><strong>{price}</strong><span>/mes</span></div>
      </div>
      <div className={styles.capacity}>
        <span><PhoneCall size={15} aria-hidden="true" /><strong>{plan.includedMinutes}</strong> min incluidos</span>
        <span><Users size={15} aria-hidden="true" /><strong>{plan.maxConcurrentCalls}</strong> llamada{plan.maxConcurrentCalls === 1 ? "" : "s"} simultánea{plan.maxConcurrentCalls === 1 ? "" : "s"}</span>
      </div>
      <ul className={styles.features}>
        <li><Check size={15} aria-hidden="true" /> Atención IA con la información configurada del negocio</li>
        <li><Check size={15} aria-hidden="true" /> Preguntas, reservas y reagendamiento según capacidades habilitadas</li>
        <li><Check size={15} aria-hidden="true" /> Transferencia a humano cuando corresponda</li>
        <li><Check size={15} aria-hidden="true" /> {overage}</li>
      </ul>
      <a className={plan.recommended ? styles.primaryPlanCta : styles.planCta} href="/">
        {plan.customPricing ? "Cotizar" : "Empezar"} <ArrowRight size={15} aria-hidden="true" />
      </a>
    </motion.article>
  );
}

export function PricingPage() {
  const pricing = useQuery({ queryKey: ["public-pricing"], queryFn: fetchPlans, retry: false });

  useEffect(() => {
    const previousTitle = document.title;
    const previousDescription = (document.querySelector('meta[name="description"]') as HTMLMetaElement | null)?.content || "";
    document.title = "Planes RecepVoz · Precios para atención con IA";
    setMetaDescription("Conoce los planes de RecepVoz para atención con IA por teléfono y habilitación controlada de WhatsApp.");
    return () => {
      document.title = previousTitle;
      setMetaDescription(previousDescription);
    };
  }, []);

  return (
    <MotionConfig reducedMotion="user">
      <div className={styles.page} data-public-pricing="true">
        <div className={styles.ambient} aria-hidden="true"><span /><span /><i /></div>

        <header className={styles.header}>
          <a className={styles.brand} href="/app/sales" aria-label="RecepVoz, experiencia comercial">
            <span className={styles.brandMark}><Sparkles size={18} aria-hidden="true" /></span>
            <span><strong>RecepVoz</strong><small>Recepcionista IA</small></span>
          </a>
          <nav className={styles.nav} aria-label="Navegación de planes">
            <a href="/app/sales">Cómo funciona</a><a href="#planes">Planes</a><a href="#incluido">Qué incluye</a>
          </nav>
          <a className={styles.headerCta} href="/">Probar RecepVoz <ArrowRight size={14} aria-hidden="true" /></a>
        </header>

        <main>
          <motion.section className={styles.hero} initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .5 }}>
            <span className={styles.eyebrow}>PLANES PARA EMPEZAR</span>
            <h1>Empieza pequeño. Mide resultados. Escala cuando realmente lo necesites.</h1>
            <p>Elige una base clara para probar RecepVoz con información real de tu negocio. La configuración inicial es asistida y el alcance se define antes de activar.</p>
            <div className={styles.heroProof}>
              <span><ShieldCheck size={15} aria-hidden="true" /> Piloto controlado</span>
              <span><Headphones size={15} aria-hidden="true" /> Voz con minutos definidos</span>
              <span><MessageCircle size={15} aria-hidden="true" /> WhatsApp con alcance acordado</span>
            </div>
          </motion.section>

          <section id="planes" className={styles.plansSection} aria-live="polite">
            {pricing.isPending && <div className={styles.loading} role="status"><span className={styles.loader} /><strong>Cargando planes…</strong><small>Consultando el catálogo comercial de RecepVoz</small></div>}
            {pricing.isError && <div className={styles.error} role="alert"><strong>No pudimos cargar los planes. Intenta nuevamente.</strong><span>El catálogo no respondió correctamente. Puedes reintentar sin generar ninguna compra.</span><button type="button" onClick={() => pricing.refetch()}>Reintentar</button></div>}
            {pricing.data && <div className={styles.planGrid}>{pricing.data.map((plan, index) => <PlanCard key={plan.code} plan={plan} index={index} />)}</div>}
          </section>

          <section id="incluido" className={styles.assuranceGrid}>
            <article><span className={styles.assuranceIcon}><PhoneCall size={18} /></span><div><strong>Voz con límites claros</strong><p>Los minutos incluidos y el valor del excedente vienen del catálogo comercial vigente.</p></div></article>
            <article><span className={styles.assuranceIcon}><MessageCircle size={18} /></span><div><strong>WhatsApp sin promesas infladas</strong><p>Se habilita de forma controlada durante el piloto y no se presenta como uso ilimitado.</p></div></article>
            <article><span className={styles.assuranceIcon}><ShieldCheck size={18} /></span><div><strong>Configuración antes de activar</strong><p>Servicios, precios, horarios y reglas se revisan antes de abrir el alcance acordado.</p></div></article>
          </section>

          <section className={styles.finalCta}>
            <div><span className={styles.eyebrow}>SIGUIENTE PASO</span><h2>Primero pruébalo. Después decides cuánto escalar.</h2><p>Esta página no inicia pagos ni suscripciones. La prueba comienza configurando tu negocio.</p></div>
            <div className={styles.finalActions}>
              <a className={styles.primaryButton} href="/">Configurar una prueba <ArrowRight size={16} /></a>
              <a className={styles.secondaryButton} href="/app/sales">Ver cómo funciona</a>
            </div>
          </section>
        </main>

        <footer className={styles.footer}><span>RecepVoz · Atención digital con IA para empresas</span><nav aria-label="Información pública"><a href="/app/sales">Producto</a><a href="/privacy.html">Privacidad</a><a href="/terms.html">Términos</a></nav></footer>
      </div>
    </MotionConfig>
  );
}
