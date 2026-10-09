import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Package } from "lucide-react";
import { getCatalogMedia, type CatalogMedia } from "../../features/inventory/api";
import type { ProductRow } from "./InventoryPage";
import styles from "./InventoryPage.module.css";

function firstSafeImage(items: CatalogMedia[] | undefined): string | null {
  const image = items?.find(item =>
    item.active !== false
    && item.mediaType === "IMAGE"
    && typeof item.mediaUrl === "string"
    && /^https:\/\/[^\s/]+/i.test(item.mediaUrl)
  );
  return image?.mediaUrl ?? null;
}

/** Limited to visible rows: page-local media queries are cached, never provider/AI calls. */
export function ProductThumbnail({
  productId,
  canReadMedia,
  large = false
}: {
  productId: string;
  canReadMedia: boolean;
  large?: boolean;
}) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const result = useQuery({
    queryKey: ["catalog", "media", productId],
    queryFn: () => getCatalogMedia(productId),
    enabled: canReadMedia,
    staleTime: 300_000,
    retry: false,
    refetchOnWindowFocus: false
  });
  const imageUrl = firstSafeImage(result.data);
  return (
    <span className={large ? styles.productThumbnailLarge : styles.productThumbnail} aria-hidden="true">
      {imageUrl && imageUrl !== failedUrl
        ? <img src={imageUrl} alt="" loading="lazy" referrerPolicy="no-referrer"
            onError={() => setFailedUrl(imageUrl)} />
        : <Package size={large ? 36 : 20} strokeWidth={1.6} />}
    </span>
  );
}

export function formatCatalogPrice(price: number | null, currency?: string): string {
  if (price === null || !Number.isFinite(price)) return "Sin precio";
  const code = currency?.trim().toUpperCase() || "CLP";
  if (!/^[A-Z]{3}$/.test(code)) return "Precio no disponible";
  try {
    return new Intl.NumberFormat("es-CL", {
      style: "currency",
      currency: code,
      maximumFractionDigits: code === "CLP" ? 0 : 2
    }).format(price);
  } catch {
    return "Precio no disponible";
  }
}

export function ProductInspector({
  row,
  canReadMedia,
  onClose,
  onHistory,
  onVariants,
  onConfigure,
  onAdjust,
  canReadVariants,
  canManageStock
}: {
  row: ProductRow;
  canReadMedia: boolean;
  onClose: () => void;
  onHistory: () => void;
  onVariants: () => void;
  onConfigure: () => void;
  onAdjust: () => void;
  canReadVariants: boolean;
  canManageStock: boolean;
}) {
  const closeButton = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const previousFocus = document.activeElement;
    closeButton.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCloseRef.current();
      }
      if (event.key === "Tab") {
        const focusable = Array.from(dialogRef.current?.querySelectorAll<HTMLButtonElement>(
          'button:not([disabled])'
        ) ?? []);
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
        previousFocus.focus();
      }
    };
  }, []);
  return (
    <div className={styles.dialogBackdrop}>
      <section ref={dialogRef} className={styles.productInspector} role="dialog" aria-modal="true"
        aria-labelledby="inventoryInspectorTitle" data-testid="inventory-inspector">
        <div className={styles.dialogHeader}>
          <div>
            <span className={styles.dialogEyebrow}>Ficha de producto</span>
            <h2 id="inventoryInspectorTitle">{row.name}</h2>
          </div>
          <button ref={closeButton} type="button" className="button ghost" onClick={onClose}>
            Cerrar detalles
          </button>
        </div>
        <div className={styles.inspectorBody}>
          <div className={styles.inspectorOverview}>
            <ProductThumbnail productId={row.id} canReadMedia={canReadMedia} large />
            <div className={styles.inspectorOverviewText}>
              <strong>{formatCatalogPrice(row.price, row.currency)}</strong>
              <span>SKU: {row.sku || "Sin SKU"}</span>
              {row.description && <p>{row.description}</p>}
            </div>
          </div>
          <dl className={styles.inspectorMetrics}>
            <div><dt>Disponible</dt><dd>{row.available ?? "—"}</dd></div>
            <div><dt>Reservado</dt><dd>{row.reserved ?? "—"}</dd></div>
            <div><dt>Físico</dt><dd>{row.onHand ?? "—"}</dd></div>
            <div><dt>Stock mínimo</dt><dd>{row.reorderThreshold ?? "—"}</dd></div>
          </dl>
          {!row.configured
            ? <p className={styles.dialogHint}>Stock sin configurar. La disponibilidad no se puede prometer.</p>
            : !row.trackingEnabled
              ? <p className={styles.dialogHint}>El control de stock está desactivado.</p>
              : row.outOfStock
                ? <p className={styles.dialogHint}>Sin unidades disponibles. El stock físico puede estar reservado.</p>
                : row.lowStock
                  ? <p className={styles.dialogHint}>Stock bajo: revisa el mínimo antes de prometer disponibilidad.</p>
                  : <p className={styles.dialogHint}>Disponibilidad calculada desde el backend.</p>}
          <div className={styles.inspectorActions}>
            {canReadVariants && <button className="button secondary" type="button" onClick={onVariants}>Variantes</button>}
            {row.configured && <button className="button secondary" type="button" onClick={onHistory}>Ver historial</button>}
            {canManageStock && <button className="button secondary" type="button" onClick={onConfigure}>
              {row.configured ? "Editar stock" : "Configurar stock"}
            </button>}
            {canManageStock && row.configured && row.trackingEnabled &&
              <button className="button primary" type="button" onClick={onAdjust}>Ajustar stock</button>}
          </div>
        </div>
      </section>
    </div>
  );
}
