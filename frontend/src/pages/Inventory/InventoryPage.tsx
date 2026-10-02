import { useMemo, useState, type ReactNode } from "react";
import { AlertTriangle, Boxes, PackageCheck, PackageOpen, Search } from "lucide-react";
import { AppShell } from "../../components/AppShell/AppShell";
import { useInventoryWorkspace } from "../../features/inventory/useInventoryWorkspace";
import type { CatalogItem, InventoryAlert, InventoryStock } from "../../features/inventory/api";
import styles from "./InventoryPage.module.css";

type StatusFilter = "ALL" | "LOW" | "OUT" | "UNCONFIGURED";
type SortMode = "ATTENTION" | "NAME_ASC" | "AVAILABLE_ASC";

interface ProductRow {
  id: string;
  name: string;
  description: string;
  sku: string;
  configured: boolean;
  trackingEnabled: boolean;
  onHand: number | null;
  reserved: number | null;
  available: number | null;
  reorderThreshold: number | null;
  lowStock: boolean;
  outOfStock: boolean;
}

function numberOrNull(value: unknown) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function buildRows(catalog: CatalogItem[], inventory: InventoryStock[], alerts: InventoryAlert[]): ProductRow[] {
  const stockByProduct = new Map(inventory.map(stock => [String(stock.catalogItemId), stock]));
  const alertTypes = new Map<string, Set<string>>();

  for (const alert of alerts) {
    if (!alert.catalogItemId || alert.acknowledged) continue;
    const id = String(alert.catalogItemId);
    const types = alertTypes.get(id) ?? new Set<string>();
    if (alert.type) types.add(String(alert.type));
    alertTypes.set(id, types);
  }

  return catalog
    .filter(item => item && item.kind === "PRODUCT" && item.active !== false)
    .map(item => {
      const stock = stockByProduct.get(String(item.id));
      const available = numberOrNull(stock?.available);
      const trackingEnabled = Boolean(stock?.trackingEnabled);
      const alertsForProduct = alertTypes.get(String(item.id));

      return {
        id: String(item.id),
        name: String(item.name || "Producto"),
        description: String(item.description || ""),
        sku: String(stock?.sku || ""),
        configured: Boolean(stock),
        trackingEnabled,
        onHand: numberOrNull(stock?.onHand),
        reserved: numberOrNull(stock?.reserved),
        available,
        reorderThreshold: numberOrNull(stock?.reorderThreshold),
        lowStock: Boolean(
          trackingEnabled
          && (stock?.lowStock || alertsForProduct?.has("LOW_STOCK"))
        ),
        outOfStock: Boolean(
          trackingEnabled
          && (available === 0 || alertsForProduct?.has("OUT_OF_STOCK"))
        )
      };
    });
}

function stockValue(value: number | null) {
  return value === null ? "—" : String(value);
}

function SummaryCard({
  label,
  value,
  detail,
  testId,
  icon
}: {
  label: string;
  value: string;
  detail: string;
  testId: string;
  icon: ReactNode;
}) {
  return (
    <article className={styles.summaryCard} data-testid={testId}>
      <span className={styles.summaryIcon} aria-hidden="true">{icon}</span>
      <div>
        <span className={styles.summaryLabel}>{label}</span>
        <strong className={styles.summaryValue}>{value}</strong>
        <small className={styles.summaryDetail}>{detail}</small>
      </div>
    </article>
  );
}

function ProductStatus({ row }: { row: ProductRow }) {
  if (!row.configured || !row.trackingEnabled) {
    return <span className={styles.statusMuted}>Sin configurar</span>;
  }
  if (row.outOfStock) {
    return <span className={styles.statusDanger}>Agotado</span>;
  }
  if (row.lowStock) {
    return <span className={styles.statusWarning}>Stock bajo</span>;
  }
  return <span className={styles.statusOk}>Disponible</span>;
}

export function InventoryPage() {
  const model = useInventoryWorkspace();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [sort, setSort] = useState<SortMode>("ATTENTION");

  const rows = useMemo(
    () => buildRows(
      model.catalog.data ?? [],
      model.inventory.data ?? [],
      model.alerts.data ?? []
    ),
    [model.catalog.data, model.inventory.data, model.alerts.data]
  );

  const visibleRows = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("es");
    const next = rows.filter(row => {
      if (query && !`${row.name} ${row.description} ${row.sku}`.toLocaleLowerCase("es").includes(query)) {
        return false;
      }
      if (status === "LOW") return row.lowStock;
      if (status === "OUT") return row.outOfStock;
      if (status === "UNCONFIGURED") return !row.configured || !row.trackingEnabled;
      return true;
    });

    if (sort === "NAME_ASC") {
      return [...next].sort((a, b) => a.name.localeCompare(b.name, "es"));
    }
    if (sort === "AVAILABLE_ASC") {
      return [...next].sort((a, b) => {
        const av = a.available ?? Number.POSITIVE_INFINITY;
        const bv = b.available ?? Number.POSITIVE_INFINITY;
        return av - bv || a.name.localeCompare(b.name, "es");
      });
    }

    const attentionRank = (row: ProductRow) => {
      if (row.outOfStock) return 0;
      if (row.lowStock) return 1;
      if (!row.configured || !row.trackingEnabled) return 2;
      return 3;
    };
    return [...next].sort((a, b) => attentionRank(a) - attentionRank(b) || a.name.localeCompare(b.name, "es"));
  }, [rows, search, status, sort]);

  const tracked = rows.filter(row => row.configured && row.trackingEnabled);
  const availableTotal = tracked.reduce((total, row) => total + (row.available ?? 0), 0);
  const reservedTotal = tracked.reduce((total, row) => total + (row.reserved ?? 0), 0);
  const lowStockTotal = tracked.filter(row => row.lowStock || row.outOfStock).length;

  const loading = model.me.isPending || model.catalog.isPending || model.inventory.isPending;
  const primaryFailed = model.catalog.isError || model.inventory.isError;
  const secondaryFailed = model.alerts.isError
    || model.restockSubscriptions.isError
    || model.restockNotifications.isError;
  const businessName = model.business.data?.name?.trim();
  const roleLabel = model.canManage ? "Gestión habilitada" : "Solo lectura";

  if (loading) {
    return (
      <AppShell>
        <main className="rv-page-frame">
          <header className="rv-page-header">
            <div>
              <p className="eyebrow">OPERACIÓN</p>
              <h1>Inventario</h1>
              <p>Stock físico, reservado y disponible en un solo lugar.</p>
            </div>
          </header>
          <div className={styles.loadingCard} role="status" aria-live="polite">
            <span className={styles.spinner} aria-hidden="true" />
            <span>Cargando inventario autoritativo…</span>
          </div>
        </main>
      </AppShell>
    );
  }

  if (primaryFailed) {
    return (
      <AppShell>
        <main className="rv-page-frame">
          <header className="rv-page-header">
            <div>
              <p className="eyebrow">OPERACIÓN</p>
              <h1>Inventario</h1>
              <p>Stock físico, reservado y disponible en un solo lugar.</p>
            </div>
          </header>

          <section className={styles.summaryGrid} aria-label="Resumen de inventario">
            <SummaryCard label="Productos" value="—" detail="Catálogo no disponible" testId="inventory-products" icon={<Boxes size={19} />} />
            <SummaryCard label="Disponible" value="—" detail="No calculado" testId="inventory-available" icon={<PackageCheck size={19} />} />
            <SummaryCard label="Reservado" value="—" detail="No calculado" testId="inventory-reserved" icon={<PackageOpen size={19} />} />
            <SummaryCard label="Atención" value="—" detail="No calculado" testId="inventory-low-stock" icon={<AlertTriangle size={19} />} />
          </section>

          <section className={styles.errorCard} role="alert">
            <strong>No pudimos cargar el inventario completo.</strong>
            <p>No mostramos cifras parciales como si fueran stock real. Puedes reintentar sin salir de esta pantalla.</p>
            <button className="button secondary" type="button" onClick={() => void model.refetchPrimary()}>
              Reintentar
            </button>
          </section>
        </main>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <main className={`rv-page-frame ${styles.page}`}>
        <header className="rv-page-header">
          <div>
            <p className="eyebrow">OPERACIÓN</p>
            <h1>Inventario</h1>
            <p>
              {businessName ? `${businessName} · ` : ""}
              Stock físico, reservado y disponible sin mezclar conceptos.
            </p>
          </div>
          <span className={styles.rolePill}>{roleLabel}</span>
        </header>

        <section className={styles.summaryGrid} aria-label="Resumen de inventario">
          <SummaryCard
            label="Productos"
            value={String(rows.length)}
            detail={`${tracked.length} con stock configurado`}
            testId="inventory-products"
            icon={<Boxes size={19} />}
          />
          <SummaryCard
            label="Disponible"
            value={tracked.length ? String(availableTotal) : "—"}
            detail="Unidades que aún se pueden ofrecer"
            testId="inventory-available"
            icon={<PackageCheck size={19} />}
          />
          <SummaryCard
            label="Reservado"
            value={tracked.length ? String(reservedTotal) : "—"}
            detail="Unidades apartadas, aún físicas"
            testId="inventory-reserved"
            icon={<PackageOpen size={19} />}
          />
          <SummaryCard
            label="Atención"
            value={String(lowStockTotal)}
            detail="Productos con stock bajo o agotado"
            testId="inventory-low-stock"
            icon={<AlertTriangle size={19} />}
          />
        </section>

        {secondaryFailed && (
          <p className={styles.partialNotice} role="status">
            El stock está disponible, pero una fuente secundaria de alertas o reposición no respondió.
          </p>
        )}

        <section className={styles.workspace} aria-labelledby="inventoryWorkspaceTitle">
          <div className={styles.workspaceHeader}>
            <div>
              <h2 id="inventoryWorkspaceTitle">Productos y stock</h2>
              <p>El stock disponible siempre viene del backend. La búsqueda y los filtros solo cambian esta vista.</p>
            </div>
            <div className={styles.queueSummary} aria-label="Resumen de reposición">
              <span>{(model.alerts.data ?? []).filter(alert => !alert.acknowledged).length} alertas</span>
              <span>{(model.restockSubscriptions.data ?? []).length} esperando reposición</span>
            </div>
          </div>

          <div className={styles.toolbar}>
            <label className={styles.searchField}>
              <span className={styles.visuallyHidden}>Buscar productos</span>
              <Search size={17} aria-hidden="true" />
              <input
                type="search"
                aria-label="Buscar productos"
                value={search}
                onChange={event => setSearch(event.target.value)}
                placeholder="Buscar por producto o SKU"
              />
            </label>

            <label className={styles.selectField}>
              <span>Estado</span>
              <select aria-label="Estado" value={status} onChange={event => setStatus(event.target.value as StatusFilter)}>
                <option value="ALL">Todos</option>
                <option value="LOW">Stock bajo</option>
                <option value="OUT">Agotado</option>
                <option value="UNCONFIGURED">Sin configurar</option>
              </select>
            </label>

            <label className={styles.selectField}>
              <span>Orden</span>
              <select aria-label="Orden" value={sort} onChange={event => setSort(event.target.value as SortMode)}>
                <option value="ATTENTION">Prioridad</option>
                <option value="NAME_ASC">Nombre A–Z</option>
                <option value="AVAILABLE_ASC">Menor disponible</option>
              </select>
            </label>
          </div>

          <div className={styles.tableScroll}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">Producto</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Disponible</th>
                  <th scope="col">Reservado</th>
                  <th scope="col">Físico</th>
                </tr>
              </thead>
              <tbody>
                {visibleRows.map(row => (
                  <tr key={row.id} data-testid={`inventory-row-${row.id}`}>
                    <td>
                      <div className={styles.productCell}>
                        <strong>{row.name}</strong>
                        <span>{row.sku || "Sin SKU"}{row.description ? ` · ${row.description}` : ""}</span>
                      </div>
                    </td>
                    <td><ProductStatus row={row} /></td>
                    <td>
                      <strong className={styles.availableValue}>{stockValue(row.available)}</strong>
                    </td>
                    <td>{stockValue(row.reserved)}</td>
                    <td>{stockValue(row.onHand)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {visibleRows.length === 0 && (
            <div className={styles.emptyState}>
              <strong>No hay productos que coincidan.</strong>
              <span>Prueba otra búsqueda o cambia el estado seleccionado.</span>
            </div>
          )}
        </section>
      </main>
    </AppShell>
  );
}
