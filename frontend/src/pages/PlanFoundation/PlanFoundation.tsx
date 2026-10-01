import { motion, useReducedMotion } from "framer-motion";
import { AppShell } from "../../components/AppShell/AppShell";
import styles from "./PlanFoundation.module.css";

export function PlanFoundation() {
  const reduceMotion = useReducedMotion();

  return (
    <AppShell>
      <main className="rv-page-frame">
        <header className="rv-page-header">
          <div>
            <p className="eyebrow">CUENTA</p>
            <h1>Plan y consumo</h1>
            <p>La nueva base React está aislada del frontend legacy mientras migramos cada pantalla con equivalencia funcional.</p>
          </div>
          <a className="button secondary" href="/account.html">Versión actual</a>
        </header>

        <motion.section
          className={styles.introCard}
          initial={reduceMotion ? false : { opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: reduceMotion ? 0 : 0.24 }}
          aria-label="Estado de la migración"
        >
          <strong>Frontend React conectado</strong>
          <p>
            Router, autenticación, caché de servidor, cliente API y shell compartido ya tienen un punto único de entrada.
            La siguiente etapa reemplaza esta tarjeta por el plan y consumo real sin retirar todavía la pantalla legacy.
          </p>
          <span className={styles.status}><span className={styles.dot} aria-hidden="true" />Base lista para migración</span>
        </motion.section>
      </main>
    </AppShell>
  );
}
