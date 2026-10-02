import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import { AlertTriangle, Boxes, PackageCheck, PackageOpen, Search } from "lucide-react";
import { AppShell } from "../../components/AppShell/AppShell";
import { useInventoryWorkspace } from "../../features/inventory/useInventoryWorkspace";
import {
  adjustInventoryStock,
  adjustInventoryVariant,
  configureInventoryStock,
  createCatalogProduct,
  createInventoryVariant,
  deactivateInventoryVariant,
  getInventoryHistory,
  getInventoryVariantHistory,
  getInventoryVariants,
  updateInventoryVariant,
  type CatalogItem,
  type InventoryAlert,
  type InventoryMovement,
  type InventoryStock,
  type InventoryVariant,
  type InventoryVariantMovement
} from "../../features/inventory/api";
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

function mutationMessage(error: unknown) {
  return error instanceof Error && error.message
    ? error.message
    : "No pudimos guardar el cambio.";
}

function movementLabel(type: InventoryMovement["type"]) {
  if (type === "CONFIGURE") return "Configuración";
  if (type === "ADJUSTMENT") return "Ajuste manual";
  if (type === "RESERVATION") return "Reserva";
  if (type === "RELEASE") return "Liberación";
  return "Consumo";
}

function signedDelta(value: number) {
  return value > 0 ? `+${value}` : String(value);
}

function movementDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("es-CL", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
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
  const [productCreateOpen, setProductCreateOpen] = useState(false);
  const [configureTarget, setConfigureTarget] = useState<ProductRow | null>(null);
  const [adjustTarget, setAdjustTarget] = useState<ProductRow | null>(null);
  const [historyTarget, setHistoryTarget] = useState<ProductRow | null>(null);
  const [historyMovements, setHistoryMovements] = useState<InventoryMovement[]>([]);
  const [historyPending, setHistoryPending] = useState(false);
  const [historyError, setHistoryError] = useState("");
  const [variantsTarget, setVariantsTarget] = useState<ProductRow | null>(null);
  const [variants, setVariants] = useState<InventoryVariant[]>([]);
  const [variantsPending, setVariantsPending] = useState(false);
  const [variantsError, setVariantsError] = useState("");
  const [variantEditorMode, setVariantEditorMode] = useState<"CREATE" | "EDIT" | null>(null);
  const [variantEditing, setVariantEditing] = useState<InventoryVariant | null>(null);
  const [variantAdjusting, setVariantAdjusting] = useState<InventoryVariant | null>(null);
  const [variantHistoryTarget, setVariantHistoryTarget] = useState<InventoryVariant | null>(null);
  const [variantHistory, setVariantHistory] = useState<InventoryVariantMovement[]>([]);
  const [variantHistoryPending, setVariantHistoryPending] = useState(false);
  const [variantHistoryError, setVariantHistoryError] = useState("");
  const [mutationPending, setMutationPending] = useState(false);
  const [mutationError, setMutationError] = useState("");

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
  const roles = model.me.data?.roles ?? [];
  const canReadVariants = roles.some(role => role === "BUSINESS_ADMIN" || role === "OPERATOR");
  const canManageVariants = roles.includes("BUSINESS_ADMIN");

  async function openHistory(row: ProductRow) {
    setHistoryTarget(row);
    setHistoryMovements([]);
    setHistoryError("");
    setHistoryPending(true);
    try {
      const movements = await getInventoryHistory(row.id);
      setHistoryMovements(movements);
    } catch (error) {
      setHistoryError(error instanceof Error && error.message
        ? error.message
        : "No pudimos cargar el historial.");
    } finally {
      setHistoryPending(false);
    }
  }

  async function refreshVariants(catalogItemId: string) {
    const data = await getInventoryVariants(catalogItemId);
    setVariants(data);
  }

  async function openVariants(row: ProductRow) {
    if (!canReadVariants) return;
    setVariantsTarget(row);
    setVariants([]);
    setVariantsError("");
    setVariantEditorMode(null);
    setVariantEditing(null);
    setVariantAdjusting(null);
    setMutationError("");
    setVariantsPending(true);
    try {
      await refreshVariants(row.id);
    } catch (error) {
      setVariantsError(error instanceof Error && error.message
        ? error.message
        : "No pudimos cargar las variantes.");
    } finally {
      setVariantsPending(false);
    }
  }

  function variantInputFromForm(
    form: HTMLFormElement,
    current: InventoryVariant | null
  ) {
    const data = new FormData(form);
    const name = String(data.get("variantName") ?? "").trim();
    const optionValuesJson = String(data.get("optionValuesJson") ?? "").trim() || "{}";
    const sku = String(data.get("variantSku") ?? "").trim().toUpperCase();
    const onHand = Number(data.get("variantOnHand"));
    const reorderThreshold = Number(data.get("variantReorderThreshold"));
    const note = String(data.get("variantNote") ?? "").trim();

    if (!name) throw new Error("Escribe un nombre para la variante.");
    if (!sku) throw new Error("Escribe un SKU para la variante.");
    if (!Number.isInteger(onHand) || onHand < 0
        || !Number.isInteger(reorderThreshold) || reorderThreshold < 0) {
      throw new Error("El stock físico y el umbral deben ser enteros iguales o mayores que cero.");
    }
    try {
      const parsed = JSON.parse(optionValuesJson);
      if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
        throw new Error("not-object");
      }
    } catch {
      throw new Error("Opciones JSON debe ser un objeto JSON válido.");
    }

    return {
      name,
      optionValuesJson,
      sku,
      trackingEnabled: current?.trackingEnabled ?? true,
      onHand,
      reorderThreshold,
      active: current?.active ?? true,
      note: note || null
    };
  }

  async function handleVariantEditor(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!variantsTarget || !canManageVariants || !variantEditorMode || mutationPending) return;

    setMutationError("");
    let input;
    try {
      input = variantInputFromForm(event.currentTarget, variantEditing);
    } catch (error) {
      setMutationError(mutationMessage(error));
      return;
    }

    setMutationPending(true);
    try {
      if (variantEditorMode === "CREATE") {
        await createInventoryVariant(variantsTarget.id, input);
      } else if (variantEditing) {
        await updateInventoryVariant(variantsTarget.id, variantEditing.id, input);
      }
      await refreshVariants(variantsTarget.id);
      setVariantEditorMode(null);
      setVariantEditing(null);
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      setMutationPending(false);
    }
  }

  async function handleVariantAdjustment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!variantsTarget || !variantAdjusting || !canManageVariants || mutationPending) return;

    const data = new FormData(event.currentTarget);
    const delta = Number(data.get("variantDelta"));
    const note = String(data.get("variantAdjustmentNote") ?? "").trim();
    if (!Number.isInteger(delta) || delta === 0) {
      setMutationError("El ajuste de variante debe ser un entero distinto de cero.");
      return;
    }

    setMutationPending(true);
    setMutationError("");
    try {
      await adjustInventoryVariant(variantsTarget.id, variantAdjusting.id, {
        delta,
        note: note || null
      });
      await refreshVariants(variantsTarget.id);
      setVariantAdjusting(null);
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      setMutationPending(false);
    }
  }

  async function deactivateVariant(variant: InventoryVariant) {
    if (!variantsTarget || !canManageVariants || mutationPending) return;
    setMutationPending(true);
    setMutationError("");
    try {
      await deactivateInventoryVariant(variantsTarget.id, variant.id);
      await refreshVariants(variantsTarget.id);
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      setMutationPending(false);
    }
  }

  async function openVariantHistory(variant: InventoryVariant) {
    if (!variantsTarget || !canReadVariants) return;
    setVariantHistoryTarget(variant);
    setVariantHistory([]);
    setVariantHistoryError("");
    setVariantHistoryPending(true);
    try {
      const movements = await getInventoryVariantHistory(variantsTarget.id, variant.id);
      setVariantHistory(movements);
    } catch (error) {
      setVariantHistoryError(error instanceof Error && error.message
        ? error.message
        : "No pudimos cargar el historial de la variante.");
    } finally {
      setVariantHistoryPending(false);
    }
  }

  async function handleCreateProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!model.canManage || mutationPending) return;

    const data = new FormData(event.currentTarget);
    const name = String(data.get("name") ?? "").trim();
    const description = String(data.get("description") ?? "").trim();
    const price = Number(data.get("price"));
    const currency = String(data.get("currency") ?? "").trim().toUpperCase();

    if (!name) {
      setMutationError("Escribe un nombre para el producto.");
      return;
    }
    if (!Number.isFinite(price) || price < 0) {
      setMutationError("El precio debe ser un número igual o mayor que cero.");
      return;
    }
    if (!/^[A-Z]{3}$/.test(currency)) {
      setMutationError("La moneda debe tener tres letras, por ejemplo CLP.");
      return;
    }

    setMutationPending(true);
    setMutationError("");
    try {
      await createCatalogProduct({
        kind: "PRODUCT",
        name,
        description: description || null,
        price,
        currency,
        durationMinutes: null,
        metadataJson: null,
        active: true
      });
      await model.refetchPrimary();
      setProductCreateOpen(false);
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      setMutationPending(false);
    }
  }

  async function handleConfigure(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!configureTarget || !model.canManage || mutationPending) return;

    const data = new FormData(event.currentTarget);
    const onHand = Number(data.get("onHand"));
    const reorderThreshold = Number(data.get("reorderThreshold"));

    if (!Number.isInteger(onHand) || onHand < 0
        || !Number.isInteger(reorderThreshold) || reorderThreshold < 0) {
      setMutationError("El stock físico y el umbral deben ser números enteros iguales o mayores que cero.");
      return;
    }

    setMutationPending(true);
    setMutationError("");
    try {
      const sku = String(data.get("sku") ?? "").trim();
      const note = String(data.get("note") ?? "").trim();
      await configureInventoryStock(configureTarget.id, {
        sku: sku || null,
        trackingEnabled: true,
        onHand,
        reorderThreshold,
        note: note || null
      });
      await model.refetchPrimary();
      setConfigureTarget(null);
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      setMutationPending(false);
    }
  }

  async function handleAdjustment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!adjustTarget || !model.canManage || mutationPending) return;

    const data = new FormData(event.currentTarget);
    const delta = Number(data.get("delta"));
    if (!Number.isInteger(delta) || delta === 0) {
      setMutationError("El ajuste debe ser un número entero distinto de cero.");
      return;
    }

    setMutationPending(true);
    setMutationError("");
    try {
      const note = String(data.get("note") ?? "").trim();
      await adjustInventoryStock(adjustTarget.id, {
        delta,
        referenceType: "MANUAL",
        referenceId: null,
        note: note || null
      });
      await model.refetchPrimary();
      setAdjustTarget(null);
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      setMutationPending(false);
    }
  }

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
            <div className={styles.workspaceActions}>
              {model.canManage && (
                <button
                  className="button primary"
                  type="button"
                  onClick={() => {
                    setMutationError("");
                    setProductCreateOpen(true);
                  }}
                >
                  Nuevo producto
                </button>
              )}
              <div className={styles.queueSummary} aria-label="Resumen de reposición">
                <span>{(model.alerts.data ?? []).filter(alert => !alert.acknowledged).length} alertas</span>
                <span>{(model.restockSubscriptions.data ?? []).length} esperando reposición</span>
              </div>
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
                  <th scope="col">Acciones</th>
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
                    <td className={styles.actionCell}>
                      {row.configured && (
                        <button
                          className="button ghost"
                          type="button"
                          onClick={() => void openHistory(row)}
                        >
                          Ver historial
                        </button>
                      )}
                      {model.canManage && (!row.configured || !row.trackingEnabled ? (
                        <button
                          className="button secondary"
                          type="button"
                          onClick={() => {
                            setMutationError("");
                            setConfigureTarget(row);
                          }}
                        >
                          Configurar stock
                        </button>
                      ) : (
                        <button
                          className="button secondary"
                          type="button"
                          onClick={() => {
                            setMutationError("");
                            setAdjustTarget(row);
                          }}
                        >
                          Ajustar stock
                        </button>
                      ))}
                    </td>
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

        {historyTarget && (
          <div className={styles.dialogBackdrop}>
            <section
              className={styles.dialog}
              role="dialog"
              aria-modal="true"
              aria-labelledby="historyTitle"
            >
              <div className={styles.dialogHeader}>
                <div>
                  <span className={styles.dialogEyebrow}>Trazabilidad</span>
                  <h2 id="historyTitle">Historial · {historyTarget.name}</h2>
                </div>
                <button
                  className="button ghost"
                  type="button"
                  onClick={() => setHistoryTarget(null)}
                >
                  Cerrar
                </button>
              </div>

              <div className={styles.historyBody}>
                {historyPending && (
                  <p className={styles.dialogHint} role="status">Cargando movimientos…</p>
                )}

                {!historyPending && historyError && (
                  <p className={styles.dialogError} role="alert">{historyError}</p>
                )}

                {!historyPending && !historyError && historyMovements.length === 0 && (
                  <p className={styles.dialogHint}>Este producto todavía no tiene movimientos registrados.</p>
                )}

                {!historyPending && !historyError && historyMovements.length > 0 && (
                  <div className={styles.movementList}>
                    {historyMovements.map(movement => (
                      <article
                        key={movement.id}
                        className={styles.movementCard}
                        data-testid={`inventory-movement-${movement.id}`}
                      >
                        <div className={styles.movementHeader}>
                          <strong>{movementLabel(movement.type)}</strong>
                          <time
                            data-testid="movement-created-at"
                            dateTime={movement.createdAt}
                          >
                            {movementDate(movement.createdAt)}
                          </time>
                        </div>

                        <div className={styles.movementDeltas}>
                          <span>Físico {signedDelta(movement.quantityDelta)}</span>
                          <span>Reservado {signedDelta(movement.reservedDelta)}</span>
                        </div>

                        <div className={styles.movementAfter}>
                          <span>Físico después: {movement.onHandAfter}</span>
                          <span>Reservado después: {movement.reservedAfter}</span>
                        </div>

                        {movement.note && <p className={styles.movementNote}>{movement.note}</p>}
                      </article>
                    ))}
                  </div>
                )}
              </div>
            </section>
          </div>
        )}

        {productCreateOpen && (
          <div className={styles.dialogBackdrop}>
            <section
              className={styles.dialog}
              role="dialog"
              aria-modal="true"
              aria-labelledby="createProductTitle"
            >
              <div className={styles.dialogHeader}>
                <div>
                  <span className={styles.dialogEyebrow}>Catálogo</span>
                  <h2 id="createProductTitle">Nuevo producto</h2>
                </div>
                <button
                  className="button ghost"
                  type="button"
                  disabled={mutationPending}
                  onClick={() => setProductCreateOpen(false)}
                >
                  Cancelar
                </button>
              </div>

              <form className={styles.dialogForm} onSubmit={handleCreateProduct}>
                <label className={styles.field}>
                  <span>Nombre</span>
                  <input name="name" autoFocus required />
                </label>

                <label className={styles.field}>
                  <span>Descripción</span>
                  <input name="description" />
                </label>

                <div className={styles.formGrid}>
                  <label className={styles.field}>
                    <span>Precio</span>
                    <input name="price" type="number" min="0" step="0.01" required />
                  </label>

                  <label className={styles.field}>
                    <span>Moneda</span>
                    <input name="currency" defaultValue="CLP" maxLength={3} required />
                  </label>
                </div>

                <p className={styles.dialogHint}>
                  El producto se crea primero en catálogo. Luego puedes configurar su SKU y stock físico.
                </p>

                {mutationError && <p className={styles.dialogError} role="alert">{mutationError}</p>}

                <div className={styles.dialogActions}>
                  <button className="button primary" type="submit" disabled={mutationPending}>
                    {mutationPending ? "Creando…" : "Crear producto"}
                  </button>
                </div>
              </form>
            </section>
          </div>
        )}

        {configureTarget && (
          <div className={styles.dialogBackdrop}>
            <section
              className={styles.dialog}
              role="dialog"
              aria-modal="true"
              aria-labelledby="configureStockTitle"
            >
              <div className={styles.dialogHeader}>
                <div>
                  <span className={styles.dialogEyebrow}>Inventario autoritativo</span>
                  <h2 id="configureStockTitle">Configurar stock · {configureTarget.name}</h2>
                </div>
                <button
                  className="button ghost"
                  type="button"
                  disabled={mutationPending}
                  onClick={() => setConfigureTarget(null)}
                >
                  Cancelar
                </button>
              </div>

              <form className={styles.dialogForm} onSubmit={handleConfigure}>
                <label className={styles.field}>
                  <span>SKU</span>
                  <input name="sku" defaultValue={configureTarget.sku} autoFocus />
                </label>

                <div className={styles.formGrid}>
                  <label className={styles.field}>
                    <span>Stock físico inicial</span>
                    <input
                      name="onHand"
                      type="number"
                      min="0"
                      step="1"
                      defaultValue={configureTarget.onHand ?? 0}
                      required
                    />
                  </label>

                  <label className={styles.field}>
                    <span>Umbral de reposición</span>
                    <input
                      name="reorderThreshold"
                      type="number"
                      min="0"
                      step="1"
                      defaultValue={configureTarget.reorderThreshold ?? 0}
                      required
                    />
                  </label>
                </div>

                <label className={styles.field}>
                  <span>Nota</span>
                  <input name="note" placeholder="Ej. carga inicial" />
                </label>

                {mutationError && <p className={styles.dialogError} role="alert">{mutationError}</p>}

                <div className={styles.dialogActions}>
                  <button className="button primary" type="submit" disabled={mutationPending}>
                    {mutationPending ? "Guardando…" : "Guardar configuración"}
                  </button>
                </div>
              </form>
            </section>
          </div>
        )}

        {adjustTarget && (
          <div className={styles.dialogBackdrop}>
            <section
              className={styles.dialog}
              role="dialog"
              aria-modal="true"
              aria-labelledby="adjustStockTitle"
            >
              <div className={styles.dialogHeader}>
                <div>
                  <span className={styles.dialogEyebrow}>Movimiento manual</span>
                  <h2 id="adjustStockTitle">Ajustar stock · {adjustTarget.name}</h2>
                </div>
                <button
                  className="button ghost"
                  type="button"
                  disabled={mutationPending}
                  onClick={() => setAdjustTarget(null)}
                >
                  Cancelar
                </button>
              </div>

              <form className={styles.dialogForm} onSubmit={handleAdjustment}>
                <p className={styles.dialogHint}>
                  Disponible ahora: <strong>{stockValue(adjustTarget.available)}</strong>.
                  Usa un valor positivo para reponer y negativo para corregir una baja.
                </p>

                <label className={styles.field}>
                  <span>Ajuste</span>
                  <input name="delta" type="number" step="1" required autoFocus />
                </label>

                <label className={styles.field}>
                  <span>Nota</span>
                  <input name="note" placeholder="Ej. reposición" />
                </label>

                {mutationError && <p className={styles.dialogError} role="alert">{mutationError}</p>}

                <div className={styles.dialogActions}>
                  <button className="button primary" type="submit" disabled={mutationPending}>
                    {mutationPending ? "Aplicando…" : "Aplicar ajuste"}
                  </button>
                </div>
              </form>
            </section>
          </div>
        )}
      </main>
    </AppShell>
  );
}
