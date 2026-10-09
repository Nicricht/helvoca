import styles from "./InventoryPage.module.css";

interface InventoryIntroProps {
  productCount: number;
  trackedCount: number;
  robotSrc: string;
  healthySrc: string;
  warningSrc: string;
}

/**
 * Compact, informational brand surface. The operational table and summary remain
 * the primary workspace. Asset URLs are supplied by InventoryPage so its
 * approved, screen-specific art inventory stays explicit at the page boundary.
 */
export function InventoryIntro({
  productCount,
  trackedCount,
  robotSrc,
  healthySrc,
  warningSrc
}: InventoryIntroProps) {
  return (
    <aside className={styles.visualHero} aria-label="Inventario inteligente RecepVoz" data-testid="inventory-intro">
      <div className={styles.visualHeroCopy}>
        <span className={styles.visualHeroKicker}>STOCK INTELIGENTE</span>
        <p className={styles.visualHeroHeadline}>
          Stock claro. <span>Respuestas confiables.</span>
        </p>
        <p>
          RecepVoz consulta la disponibilidad registrada antes de ofrecer un producto.
        </p>
        <div className={styles.visualHeroSignals}>
          <span>
            <i data-tone={productCount > trackedCount ? "warning" : "green"} aria-hidden="true" />
            {trackedCount} de {productCount} productos con stock controlado
          </span>
        </div>
      </div>
      <div className={styles.visualHeroArt} aria-hidden="true">
        <span className={styles.visualHeroOrbit} />
        <img className={styles.visualHeroRobot} src={robotSrc} alt="" />
        <img className={styles.visualHealthy} src={healthySrc} alt="" />
        <img className={styles.visualWarning} src={warningSrc} alt="" />
      </div>
    </aside>
  );
}
