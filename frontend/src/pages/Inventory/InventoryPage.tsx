import { useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { AlertTriangle, Boxes, PackageCheck, PackageOpen, Search } from "lucide-react";
import { ApiError } from "../../api/client";
import { AppShell } from "../../components/AppShell/AppShell";
import { useInventoryWorkspace } from "../../features/inventory/useInventoryWorkspace";
import {
  acknowledgeInventoryAlert,
  adjustInventoryStock,
  adjustInventoryVariant,
  configureInventoryStock,
  createCatalogProduct,
  createInventoryVariant,
  deactivateInventoryVariant,
  cancelRestockSubscription,
  getInventoryHistory,
  getInventoryVariantHistory,
  getInventoryVariants,
  updateCatalogProduct,
  updateInventoryVariant,
  type CatalogItem,
  type InventoryAlert,
  type InventoryMovement,
  type InventoryStock,
  type InventoryVariant,
  type InventoryVariantMovement
} from "../../features/inventory/api";
import styles from "./InventoryPage.module.css";
import { InventoryIntro } from "./InventoryIntro";
import { VariantOptionsEditor, serializeVariantOptions } from "./VariantOptionsEditor";
import { ProductInspector, ProductThumbnail, formatCatalogPrice } from "./InventoryProductPresentation";

type StatusFilter = "ALL" | "TRACKED" | "LOW" | "OUT" | "RESTOCKED" | "UNCONFIGURED";
type SortMode = "ATTENTION" | "NAME_ASC" | "AVAILABLE_ASC" | "AVAILABLE_DESC";
const PAGE_SIZE = 8;
const PERMISSION_CHANGE_ERROR = "Tus permisos cambiaron. Actualiza para continuar.";

export interface ProductRow {
  id: string;
  name: string;
  description: string;
  price: number | null;
  currency: string;
  sku: string;
  configured: boolean;
  trackingEnabled: boolean;
  onHand: number | null;
  reserved: number | null;
  available: number | null;
  reorderThreshold: number | null;
  lowStock: boolean;
  outOfStock: boolean;
  restocked: boolean;
}

function isRecordList(value: unknown): boolean {
  return Array.isArray(value) && value.every(
    item => item !== null && typeof item === "object" && !Array.isArray(item)
  );
}

function asList<T>(value: T[] | null | undefined): T[] {
  return Array.isArray(value)
    ? value.filter(item => item !== null && typeof item === "object" && !Array.isArray(item))
    : [];
}

function numberOrNull(value: unknown) {
  if (value === null || value === undefined || (typeof value === "string" && value.trim() === "")) return null;
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
        price: numberOrNull(item.price),
        currency: String(item.currency || "CLP"),
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
        ),
        restocked: Boolean(alertsForProduct?.has("RESTOCKED"))
      };
    });
}

function stockValue(value: number | null) {
  return value === null ? "—" : String(value);
}

function mutationMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 409 && /sku/i.test(error.message)) {
      return "Ese SKU ya está en uso. Elige otro SKU.";
    }
    if (error.status === 409) {
      return "El cambio entra en conflicto con el estado actual. Actualiza los datos e intenta nuevamente.";
    }
    if (error.status === 400) {
      return "Revisa los datos ingresados e intenta nuevamente.";
    }
    if (error.status >= 500) {
      return "El servidor no pudo guardar el cambio. Intenta nuevamente.";
    }
  }
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

function alertLabel(type?: string) {
  if (type === "OUT_OF_STOCK") return "Agotado";
  if (type === "LOW_STOCK") return "Stock bajo";
  if (type === "RESTOCKED") return "Repuesto";
  return "Alerta de inventario";
}

function channelLabel(channel?: string) {
  if (channel === "WHATSAPP") return "WhatsApp";
  if (channel === "EMAIL") return "Email";
  if (channel === "SMS") return "SMS";
  return channel || "Canal";
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
  if (!row.configured) {
    return <span className={styles.statusMuted}>Sin configurar</span>;
  }
  if (!row.trackingEnabled) {
    return <span className={styles.statusMuted}>Control desactivado</span>;
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
  const [page, setPage] = useState(1);
  const [inspectorTarget, setInspectorTarget] = useState<ProductRow | null>(null);
  const [productCreateOpen, setProductCreateOpen] = useState(false);
  const [productEditing, setProductEditing] = useState<CatalogItem | null>(null);
  const [refreshPending, setRefreshPending] = useState(false);
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
  const mutationLock = useRef(false);
  const [mutationError, setMutationError] = useState("");

  function beginMutation() {
    if (mutationLock.current) return false;
    mutationLock.current = true;
    setMutationPending(true);
    return true;
  }

  function endMutation() {
    mutationLock.current = false;
    setMutationPending(false);
  }

  const rows = useMemo(
    () => buildRows(
      asList(model.catalog.data),
      asList(model.inventory.data),
      asList(model.alerts.data)
    ),
    [model.catalog.data, model.inventory.data, model.alerts.data]
  );

  const visibleRows = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("es");
    const next = rows.filter(row => {
      if (query && !`${row.name} ${row.description} ${row.sku}`.toLocaleLowerCase("es").includes(query)) {
        return false;
      }
      if (status === "TRACKED") return row.configured;
      if (status === "LOW") return row.lowStock;
      if (status === "OUT") return row.outOfStock;
      if (status === "RESTOCKED") return row.restocked;
      if (status === "UNCONFIGURED") return !row.configured;
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
    if (sort === "AVAILABLE_DESC") {
      return [...next].sort((a, b) => {
        const av = a.available ?? Number.NEGATIVE_INFINITY;
        const bv = b.available ?? Number.NEGATIVE_INFINITY;
        return bv - av || a.name.localeCompare(b.name, "es");
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

  const totalPages = Math.max(1, Math.ceil(visibleRows.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pagedRows = visibleRows.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  const tracked = rows.filter(row => row.configured && row.trackingEnabled);
  const availableTotal = tracked.reduce((total, row) => total + (row.available ?? 0), 0);
  const reservedTotal = tracked.reduce((total, row) => total + (row.reserved ?? 0), 0);
  const lowStockTotal = tracked.filter(row => row.lowStock || row.outOfStock).length;

  const loading = model.me.isPending || model.catalog.isPending || model.inventory.isPending;
  const primaryFailed = model.catalog.isError || model.inventory.isError
    || (model.catalog.isSuccess && !isRecordList(model.catalog.data))
    || (model.inventory.isSuccess && !isRecordList(model.inventory.data));
  const alertsUnavailable = model.alerts.isError
    || (model.alerts.isSuccess && !isRecordList(model.alerts.data));
  const waitingUnavailable = model.restockSubscriptions.isError
    || (model.restockSubscriptions.isSuccess && !isRecordList(model.restockSubscriptions.data));
  const notificationsUnavailable = model.restockNotifications.isError
    || (model.restockNotifications.isSuccess && !isRecordList(model.restockNotifications.data));
  const secondaryFailed = alertsUnavailable || waitingUnavailable || notificationsUnavailable;
  const alertCount = alertsUnavailable || model.alerts.isPending
    ? null : (asList(model.alerts.data)).filter(alert => !alert.acknowledged).length;
  const waitingCount = waitingUnavailable || model.restockSubscriptions.isPending
    ? null : (asList(model.restockSubscriptions.data)).length;
  const notificationCount = notificationsUnavailable || model.restockNotifications.isPending
    ? null : (asList(model.restockNotifications.data)).length;
  const lastPrimarySync = Math.min(model.catalog.dataUpdatedAt || 0, model.inventory.dataUpdatedAt || 0);
  const lastPrimarySyncText = lastPrimarySync > 0
    ? new Date(lastPrimarySync).toLocaleTimeString("es-CL", { hour: "2-digit", minute: "2-digit" })
    : "No disponible";
  const businessName = model.business.data?.name?.trim();
  const roleLabel = model.canManage ? "Gestión habilitada" : "Solo lectura";
  const roles = model.me.data?.roles ?? [];
  const canReadVariants = roles.some(role => role === "BUSINESS_ADMIN" || role === "OPERATOR");
  const canManageVariants = roles.includes("BUSINESS_ADMIN") && model.canManageStock;
  const canReadAutomation = canReadVariants;
  const canReadMedia = canReadVariants;
  const canManageAutomation = roles.includes("BUSINESS_ADMIN") && model.canManageStock;

  async function refreshWorkspace() {
    if (refreshPending) return;
    setRefreshPending(true);
    try {
      await model.refetchPrimary();
    } finally {
      setRefreshPending(false);
    }
  }

  async function acknowledgeAlert(alertId: string) {
    if (!canManageAutomation || !beginMutation()) return;
    setMutationError("");
    try {
      await acknowledgeInventoryAlert(alertId);
      await model.refetchPrimary();
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      endMutation();
    }
  }

  async function restockFromAlert(catalogItemId?: string, variantId?: string | null) {
    if (!canManageAutomation || !catalogItemId) return;
    const row = rows.find(candidate => candidate.id === String(catalogItemId));
    if (!row) return;
    setMutationError("");

    if (variantId) {
      const loaded = await openVariants(row);
      const variant = loaded?.find(candidate => candidate.id === String(variantId));
      if (!variant || !variant.active || !variant.trackingEnabled) {
        setMutationError("La variante ya no está disponible para reposición.");
        return;
      }
      setVariantEditorMode(null);
      setVariantEditing(null);
      setVariantAdjusting(variant);
      return;
    }

    if (!row.configured || !row.trackingEnabled) {
      setMutationError("Configura el stock y activa su seguimiento antes de reponer.");
      return;
    }
    setAdjustTarget(row);
  }

  async function cancelWaiting(subscriptionId: string) {
    if (!canManageAutomation || !beginMutation()) return;
    setMutationError("");
    try {
      await cancelRestockSubscription(subscriptionId);
      await model.refetchPrimary();
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      endMutation();
    }
  }

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
    return data;
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
      return await refreshVariants(row.id);
    } catch (error) {
      setVariantsError(error instanceof Error && error.message
        ? error.message
        : "No pudimos cargar las variantes.");
    } finally {
      setVariantsPending(false);
    }
    return undefined;
  }

  function variantInputFromForm(form: HTMLFormElement) {
    const data = new FormData(form);
    const name = String(data.get("variantName") ?? "").trim();
    const optionValuesJson = serializeVariantOptions(form);
    const sku = String(data.get("variantSku") ?? "").trim().toUpperCase();
    const onHand = numberOrNull(data.get("variantOnHand"));
    const reorderThreshold = numberOrNull(data.get("variantReorderThreshold"));
    const note = String(data.get("variantNote") ?? "").trim();

    if (!name) throw new Error("Escribe un nombre para la variante.");
    if (!sku) throw new Error("Escribe un SKU para la variante.");
    if (onHand === null || !Number.isInteger(onHand) || onHand < 0
        || reorderThreshold === null || !Number.isInteger(reorderThreshold) || reorderThreshold < 0) {
      throw new Error("El stock físico y el umbral deben ser enteros iguales o mayores que cero.");
    }
    return {
      name,
      optionValuesJson,
      sku,
      trackingEnabled: data.get("variantTrackingEnabled") === "on",
      onHand,
      reorderThreshold,
      active: data.get("variantActive") === "on",
      note: note || null
    };
  }

  async function handleVariantEditor(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!variantsTarget || !variantEditorMode || mutationLock.current) return;
    if (!canManageVariants) {
      setMutationError(PERMISSION_CHANGE_ERROR);
      return;
    }

    setMutationError("");
    let input;
    try {
      input = variantInputFromForm(event.currentTarget);
    } catch (error) {
      setMutationError(mutationMessage(error));
      return;
    }

    if (!beginMutation()) return;
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
      endMutation();
    }
  }

  async function handleVariantAdjustment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!variantsTarget || !variantAdjusting || mutationLock.current) return;
    if (!canManageVariants) {
      setMutationError(PERMISSION_CHANGE_ERROR);
      return;
    }

    const data = new FormData(event.currentTarget);
    const delta = Number(data.get("variantDelta"));
    const note = String(data.get("variantAdjustmentNote") ?? "").trim();
    if (!Number.isInteger(delta) || delta === 0) {
      setMutationError("El ajuste de variante debe ser un entero distinto de cero.");
      return;
    }

    if (!beginMutation()) return;
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
      endMutation();
    }
  }

  async function deactivateVariant(variant: InventoryVariant) {
    if (!variantsTarget || !canManageVariants || !beginMutation()) return;
    setMutationError("");
    try {
      await deactivateInventoryVariant(variantsTarget.id, variant.id);
      await refreshVariants(variantsTarget.id);
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      endMutation();
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
    if (mutationLock.current) return;
    if (!model.canManageCatalog) {
      setMutationError(PERMISSION_CHANGE_ERROR);
      return;
    }

    const data = new FormData(event.currentTarget);
    const name = String(data.get("name") ?? "").trim();
    const description = String(data.get("description") ?? "").trim();
    const price = numberOrNull(data.get("price"));
    const currency = String(data.get("currency") ?? "").trim().toUpperCase();
    const submitter = (event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
    const continueToStock = !productEditing && model.canManageStock && submitter?.value === "configure";

    if (!name) {
      setMutationError("Escribe un nombre para el producto.");
      return;
    }
    if (price === null || price < 0) {
      setMutationError("El precio debe ser un número igual o mayor que cero.");
      return;
    }
    if (!/^[A-Z]{3}$/.test(currency)) {
      setMutationError("La moneda debe tener tres letras, por ejemplo CLP.");
      return;
    }

    if (!beginMutation()) return;
    setMutationError("");
    try {
      const input = {
        kind: "PRODUCT" as const,
        name,
        description: description || null,
        price,
        currency,
        durationMinutes: productEditing?.durationMinutes ?? null,
        metadataJson: productEditing?.metadataJson ?? null,
        active: productEditing?.active !== false
      };
      const saved = productEditing
        ? await updateCatalogProduct(productEditing.id, input)
        : await createCatalogProduct(input);
      // A successful catalogue write must not be replayed if a later read fails.
      // Stock configuration is deliberately a second, independent operation.
      setProductCreateOpen(false);
      setProductEditing(null);
      if (continueToStock && saved?.id) {
        setConfigureTarget({
          id: String(saved.id),
          name: String(saved.name || name),
          description: String(saved.description || description),
          price: numberOrNull(saved.price),
          currency: String(saved.currency || currency),
          sku: "",
          configured: false,
          trackingEnabled: false,
          onHand: null,
          reserved: null,
          available: null,
          reorderThreshold: null,
          lowStock: false,
          outOfStock: false,
          restocked: false
        });
      }
      const primaryRefreshed = await model.refetchPrimary();
      if (!primaryRefreshed) {
        setMutationError("El producto se guardó, pero no se pudo actualizar la lista. Pulsa Actualizar antes de repetir cualquier operación.");
      }
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      endMutation();
    }
  }

  async function handleConfigure(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!configureTarget || mutationLock.current) return;
    if (!model.canManageStock) {
      setMutationError(PERMISSION_CHANGE_ERROR);
      return;
    }

    const data = new FormData(event.currentTarget);
    const onHand = numberOrNull(data.get("onHand"));
    const reorderThreshold = numberOrNull(data.get("reorderThreshold"));

    if (onHand === null || !Number.isInteger(onHand) || onHand < 0
        || reorderThreshold === null || !Number.isInteger(reorderThreshold) || reorderThreshold < 0) {
      setMutationError("El stock físico y el umbral deben ser números enteros iguales o mayores que cero.");
      return;
    }

    if (!beginMutation()) return;
    setMutationError("");
    try {
      const sku = String(data.get("sku") ?? "").trim();
      const note = String(data.get("note") ?? "").trim();
      await configureInventoryStock(configureTarget.id, {
        sku: sku || null,
        trackingEnabled: data.get("trackingEnabled") === "on",
        onHand,
        reorderThreshold,
        note: note || null
      });
      await model.refetchPrimary();
      setConfigureTarget(null);
    } catch (error) {
      setMutationError(mutationMessage(error));
    } finally {
      endMutation();
    }
  }

  async function handleAdjustment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!adjustTarget || mutationLock.current) return;
    if (!model.canManageStock) {
      setMutationError(PERMISSION_CHANGE_ERROR);
      return;
    }

    const data = new FormData(event.currentTarget);
    const delta = Number(data.get("delta"));
    if (!Number.isInteger(delta) || delta === 0) {
      setMutationError("El ajuste debe ser un número entero distinto de cero.");
      return;
    }

    if (!beginMutation()) return;
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
      endMutation();
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
          <div className={styles.loadingCard} role="status" aria-live="polite" data-testid="inventory-loading">
            <span className={styles.spinner} aria-hidden="true" />
            <span>Cargando inventario autoritativo…</span>
            <div className={styles.loadingSkeleton} aria-hidden="true" data-testid="inventory-loading-skeleton">
              <span /><span /><span /><span />
              <span className={styles.loadingSkeletonTable} />
            </div>
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
            {mutationError && <p className={styles.dialogError}>{mutationError}</p>}
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
      <main className={`rv-page-frame ${styles.page}`} data-visual-page="inventory">
        <div className={styles.inventoryLead}>
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
          <InventoryIntro
            productCount={rows.length}
            trackedCount={tracked.length}
            robotSrc="/app/assets/recepvoz/v2/inventory/hero-stock-robot.webp"
            healthySrc="/app/assets/recepvoz/v2/inventory/stock-confirmed.webp"
            warningSrc="/app/assets/recepvoz/v2/inventory/stock-warning.webp"
          />
        </div>

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
          <div className={styles.partialNotice} role="status" data-testid="inventory-partial-error">
            <span>
              El stock principal está disponible, pero no se pudieron consultar algunos datos de alertas o reposición.
              Los valores no disponibles se muestran como «—», nunca como cero.
            </span>
            <button className="button secondary" type="button" disabled={refreshPending}
              onClick={() => void refreshWorkspace()}>
              {refreshPending ? "Reintentando…" : "Reintentar consultas"}
            </button>
          </div>
        )}

        {rows.some(row => !row.configured) && (
          <section
            className={styles.setupNotice}
            aria-label="Productos sin stock configurado"
          >
            <div>
              <strong>
                {rows.filter(row => !row.configured).length} {rows.filter(row => !row.configured).length === 1 ? "producto" : "productos"} sin stock configurado
              </strong>
              <span>La IA solo puede prometer disponibilidad cuando el stock está configurado.</span>
            </div>
            <button
              className="button secondary"
              type="button"
              onClick={() => setStatus("UNCONFIGURED")}
            >
              Ver sin configurar
            </button>
          </section>
        )}

        <section className={styles.workspace} aria-labelledby="inventoryWorkspaceTitle">
          <div className={styles.workspaceHeader}>
            <div>
              <h2 id="inventoryWorkspaceTitle">Productos y stock</h2>
              <p>El stock disponible siempre viene del backend. La búsqueda y los filtros solo cambian esta vista.</p>
            </div>
            <div className={styles.workspaceActions}>
              <span className={styles.lastSync} data-testid="inventory-last-sync">
                Última consulta de catálogo y stock: {lastPrimarySyncText}
              </span>
              <div className={styles.workspaceButtonRow}>
                {model.canManageCatalog && (
                  <>
                    <a className="button secondary" href="/app/settings/import">
                      Importar archivos
                    </a>
                    <button
                      className="button primary"
                      type="button"
                      onClick={() => {
                        setMutationError("");
                        setProductEditing(null);
                        setProductCreateOpen(true);
                      }}
                    >
                      Nuevo producto
                    </button>
                  </>
                )}
                <button
                  className="button ghost"
                  type="button"
                  disabled={refreshPending}
                  aria-busy={refreshPending}
                  onClick={() => void refreshWorkspace()}
                >
                  {refreshPending ? "Actualizando…" : "Actualizar"}
                </button>
              </div>
              <div className={styles.queueSummary} aria-label="Resumen de reposición">
                <span>{alertCount === null ? "— alertas (sin datos)" : `${alertCount} alertas`}</span>
                <span>{waitingCount === null ? "— esperando (sin datos)" : `${waitingCount} esperando reposición`}</span>
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
                onChange={event => { setSearch(event.target.value); setPage(1); }}
                placeholder="Buscar por producto o SKU"
              />
            </label>

            <label className={styles.selectField}>
              <span>Estado</span>
              <select aria-label="Estado" value={status} onChange={event => { setStatus(event.target.value as StatusFilter); setPage(1); }}>
                <option value="ALL">Todos</option>
                <option value="TRACKED">Stock configurado</option>
                <option value="LOW">Stock bajo</option>
                <option value="OUT">Agotado</option>
                <option value="RESTOCKED">Repuestos</option>
                <option value="UNCONFIGURED">Sin configurar</option>
              </select>
            </label>

            <label className={styles.selectField}>
              <span>Orden</span>
              <select aria-label="Orden" value={sort} onChange={event => { setSort(event.target.value as SortMode); setPage(1); }}>
                <option value="ATTENTION">Atención primero</option>
                <option value="NAME_ASC">Nombre A–Z</option>
                <option value="AVAILABLE_ASC">Disponible: menor a mayor</option>
                <option value="AVAILABLE_DESC">Disponible: mayor a menor</option>
              </select>
            </label>
          </div>

          <div className={styles.tableScroll}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">Producto</th>
                  <th scope="col">Precio</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Disponible</th>
                  <th scope="col">Reservado</th>
                  <th scope="col">Físico</th>
                  <th scope="col">Mínimo</th>
                  <th scope="col">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {pagedRows.map(row => (
                  <tr key={row.id} data-testid={`inventory-row-${row.id}`}>
                    <td>
                      <div className={styles.productIdentity}>
                        <ProductThumbnail productId={row.id} canReadMedia={canReadMedia} />
                        <div className={styles.productCell}>
                          <strong>{row.name}</strong>
                          <span>{row.sku || "Sin SKU"}{row.description ? ` · ${row.description}` : ""}</span>
                          <button className={styles.productInspectLink} type="button"
                            onClick={() => setInspectorTarget(row)}
                            aria-label={`Ver detalles de ${row.name}`}>
                            Ver detalles
                          </button>
                        </div>
                      </div>
                    </td>
                    <td className={styles.productPrice}>{formatCatalogPrice(row.price, row.currency)}</td>
                    <td><ProductStatus row={row} /></td>
                    <td>
                      <strong className={styles.availableValue}>{stockValue(row.available)}</strong>
                    </td>
                    <td>{stockValue(row.reserved)}</td>
                    <td>{stockValue(row.onHand)}</td>
                    <td>{stockValue(row.reorderThreshold)}</td>
                    <td className={styles.actionCell}>
                      {canReadVariants && (
                        <button
                          className="button ghost"
                          type="button"
                          onClick={() => void openVariants(row)}
                        >
                          Variantes
                        </button>
                      )}
                      {row.configured && (
                        <button
                          className="button ghost"
                          type="button"
                          onClick={() => void openHistory(row)}
                        >
                          Ver historial
                        </button>
                      )}
                      {model.canManageStock && (
                        <button
                          className="button ghost"
                          type="button"
                          onClick={() => {
                            setMutationError("");
                            setConfigureTarget(row);
                          }}
                        >
                          {row.configured ? "Editar stock" : "Configurar stock"}
                        </button>
                      )}
                      {model.canManageStock && row.configured && row.trackingEnabled && (
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
                      )}
                      {model.canManageCatalog && (
                        <button
                          className="button ghost"
                          type="button"
                          aria-label={`Editar producto ${row.name}`}
                          onClick={() => {
                            const item = (asList(model.catalog.data)).find(candidate => String(candidate.id) === row.id);
                            if (!item) return;
                            setMutationError("");
                            setProductEditing(item);
                            setProductCreateOpen(true);
                          }}
                        >
                          Editar
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {visibleRows.length > PAGE_SIZE && (
            <nav className={styles.productPagination} aria-label="Paginación de productos">
              <span>
                Mostrando {(currentPage - 1) * PAGE_SIZE + 1}–{Math.min(currentPage * PAGE_SIZE, visibleRows.length)} de {visibleRows.length}
              </span>
              <div>
                <button className="button secondary" type="button" disabled={currentPage === 1}
                  aria-label="Página anterior" onClick={() => setPage(currentPage - 1)}>
                  Anterior
                </button>
                <span aria-live="polite">Página {currentPage} de {totalPages}</span>
                <button className="button secondary" type="button" disabled={currentPage === totalPages}
                  aria-label="Página siguiente" onClick={() => setPage(currentPage + 1)}>
                  Siguiente
                </button>
              </div>
            </nav>
          )}

          {visibleRows.length === 0 && (
            <div className={styles.emptyState}>
              <strong>No hay productos que coincidan.</strong>
              <span>Prueba otra búsqueda o cambia el estado seleccionado.</span>
            </div>
          )}
        </section>

        {canReadAutomation && (
          <section
            className={styles.automationWorkspace}
            aria-labelledby="inventoryAutomationTitle"
          >
            <div className={styles.workspaceHeader}>
              <div>
                <h2 id="inventoryAutomationTitle">Alertas y reposición</h2>
                <p>
                  Revisa quiebres de stock, personas esperando reposición y avisos que ya están listos para enviar.
                </p>
              </div>
              <div className={styles.automationSummary}>
                <span>{alertCount === null ? "— alertas (sin datos)" : `${alertCount} alertas pendientes`}</span>
                <span>{waitingCount === null ? "— esperando (sin datos)" : `${waitingCount} esperando reposición`}</span>
                <span>{notificationCount === null ? "— avisos (sin datos)" : `${notificationCount} ${notificationCount === 1 ? "aviso listo" : "avisos listos"}`}</span>
              </div>
            </div>

            {mutationError && !configureTarget && !adjustTarget && !productCreateOpen
              && !variantsTarget && !variantEditorMode && !variantAdjusting && (
                <p className={styles.automationError} role="alert">{mutationError}</p>
              )}

            <div className={styles.automationGrid}>
              <section className={styles.automationPanel} aria-labelledby="inventoryAlertsTitle">
                <div className={styles.panelHeader}>
                  <div>
                    <span className={styles.dialogEyebrow}>Stock</span>
                    <h3 id="inventoryAlertsTitle">Alertas</h3>
                  </div>
                  <strong>{alertCount ?? "—"}</strong>
                </div>

                <div className={styles.automationList}>
                  {alertsUnavailable ? (
                    <p className={styles.automationEmpty} role="status">Alertas no disponibles. Reintenta la consulta.</p>
                  ) : model.alerts.isPending ? (
                    <p className={styles.automationEmpty} role="status">Consultando alertas…</p>
                  ) : (asList(model.alerts.data)).length === 0 ? (
                    <p className={styles.automationEmpty}>No hay alertas abiertas.</p>
                  ) : (
                    (asList(model.alerts.data)).map(alert => (
                      <article
                        key={alert.id}
                        className={styles.automationCard}
                        data-testid={`inventory-alert-${alert.id}`}
                      >
                        <div className={styles.automationCardHeader}>
                          <div>
                            <strong>{alertLabel(alert.type)}</strong>
                            <span>{alert.subjectName || "Producto"}</span>
                          </div>
                          <span className={alert.acknowledged ? styles.statusMuted : styles.statusWarning}>
                            {alert.acknowledged ? "Atendida" : "Pendiente"}
                          </span>
                        </div>

                        <div className={styles.automationMeta}>
                          {alert.sku && <span>SKU: {alert.sku}</span>}
                          <span>Disponible: {alert.available ?? "—"}</span>
                          <span>Umbral: {alert.reorderThreshold ?? "—"}</span>
                        </div>

                        {canManageAutomation && (
                          <div className={styles.variantActions}>
                            {!alert.acknowledged && (
                              <button
                                className="button ghost"
                                type="button"
                                disabled={mutationPending}
                                onClick={() => void acknowledgeAlert(alert.id)}
                              >
                                Marcar atendida
                              </button>
                            )}
                            {(alert.type === "LOW_STOCK" || alert.type === "OUT_OF_STOCK")
                              && rows.some(row => row.id === String(alert.catalogItemId))
                              && (
                                <button
                                  className="button secondary"
                                  type="button"
                                  disabled={mutationPending}
                                  onClick={() => void restockFromAlert(alert.catalogItemId, alert.variantId)}
                                >
                                  Reponer stock
                                </button>
                              )}
                          </div>
                        )}
                      </article>
                    ))
                  )}
                </div>
              </section>

              <section className={styles.automationPanel} aria-labelledby="restockWaitingTitle">
                <div className={styles.panelHeader}>
                  <div>
                    <span className={styles.dialogEyebrow}>Clientes</span>
                    <h3 id="restockWaitingTitle">Esperando reposición</h3>
                  </div>
                  <strong>{waitingCount ?? "—"}</strong>
                </div>

                <div className={styles.automationList}>
                  {waitingUnavailable ? (
                    <p className={styles.automationEmpty} role="status">Lista de espera no disponible. Reintenta la consulta.</p>
                  ) : model.restockSubscriptions.isPending ? (
                    <p className={styles.automationEmpty} role="status">Consultando lista de espera…</p>
                  ) : (asList(model.restockSubscriptions.data)).length === 0 ? (
                    <p className={styles.automationEmpty}>Nadie está esperando reposición.</p>
                  ) : (
                    (asList(model.restockSubscriptions.data)).map(subscription => (
                      <article
                        key={subscription.id}
                        className={styles.automationCard}
                        data-testid={`restock-subscription-${subscription.id}`}
                      >
                        <div className={styles.automationCardHeader}>
                          <div>
                            <strong>{subscription.contact || "Contacto"}</strong>
                            <span>{channelLabel(subscription.preferredChannel)}</span>
                          </div>
                          <span className={styles.statusWarning}>Esperando</span>
                        </div>

                        {canManageAutomation && (
                          <div className={styles.variantActions}>
                            <button
                              className="button ghost"
                              type="button"
                              disabled={mutationPending}
                              onClick={() => void cancelWaiting(subscription.id)}
                            >
                              Cancelar espera
                            </button>
                          </div>
                        )}
                      </article>
                    ))
                  )}
                </div>
              </section>

              <section className={styles.automationPanel} aria-labelledby="restockNotificationsTitle">
                <div className={styles.panelHeader}>
                  <div>
                    <span className={styles.dialogEyebrow}>Avisos</span>
                    <h3 id="restockNotificationsTitle">Listos para enviar</h3>
                  </div>
                  <strong>{notificationCount ?? "—"}</strong>
                </div>

                <div className={styles.automationList}>
                  {notificationsUnavailable ? (
                    <p className={styles.automationEmpty} role="status">Avisos no disponibles. Reintenta la consulta.</p>
                  ) : model.restockNotifications.isPending ? (
                    <p className={styles.automationEmpty} role="status">Consultando avisos…</p>
                  ) : (asList(model.restockNotifications.data)).length === 0 ? (
                    <p className={styles.automationEmpty}>No hay avisos pendientes.</p>
                  ) : (
                    (asList(model.restockNotifications.data)).map(notification => (
                      <article
                        key={notification.id}
                        className={styles.automationCard}
                        data-testid={`restock-notification-${notification.id}`}
                      >
                        <div className={styles.automationCardHeader}>
                          <div>
                            <strong>{notification.subjectName || "Producto repuesto"}</strong>
                            <span>{notification.contact || "Contacto"} · {channelLabel(notification.preferredChannel)}</span>
                          </div>
                          <span className={styles.statusOk}>Pendiente de envío</span>
                        </div>

                        <div className={styles.automationMeta}>
                          {notification.sku && <span>SKU: {notification.sku}</span>}
                          <span>Disponible: {notification.available ?? "—"}</span>
                        </div>
                      </article>
                    ))
                  )}
                </div>
              </section>
            </div>
          </section>
        )}

        {/* Inventory dialogs must escape AppShell routeStage transform containment. */}
        {createPortal(
          <>
        {variantsTarget && (
          <div className={styles.dialogBackdrop}>
            <section
              className={styles.dialog}
              role="dialog"
              aria-modal="true"
              aria-labelledby="variantsTitle"
            >
              <div className={styles.dialogHeader}>
                <div>
                  <span className={styles.dialogEyebrow}>Variantes</span>
                  <h2 id="variantsTitle">Variantes · {variantsTarget.name}</h2>
                </div>
                <div className={styles.dialogHeaderActions}>
                  {canManageVariants && (
                    <button
                      className="button secondary"
                      type="button"
                      onClick={() => {
                        setMutationError("");
                        setVariantEditing(null);
                        setVariantEditorMode("CREATE");
                        setVariantAdjusting(null);
                      }}
                    >
                      Nueva variante
                    </button>
                  )}
                  <button
                    className="button ghost"
                    type="button"
                    disabled={mutationPending}
                    onClick={() => {
                      setVariantsTarget(null);
                      setVariantEditorMode(null);
                      setVariantEditing(null);
                      setVariantAdjusting(null);
                    }}
                  >
                    Cerrar
                  </button>
                </div>
              </div>

              <div className={styles.variantBody}>
                {variantsPending && (
                  <p className={styles.dialogHint} role="status">Cargando variantes…</p>
                )}

                {!variantsPending && variantsError && (
                  <p className={styles.dialogError} role="alert">{variantsError}</p>
                )}

                {mutationError && !variantEditorMode && !variantAdjusting && (
                  <p className={styles.dialogError} role="alert">{mutationError}</p>
                )}

                {!variantsPending && !variantsError && (
                  <>
                    {variantEditorMode && (
                      <form className={styles.variantForm} onSubmit={handleVariantEditor}>
                        <h3>{variantEditorMode === "CREATE" ? "Nueva variante" : "Editar variante"}</h3>

                        <label className={styles.field}>
                          <span>Nombre de variante</span>
                          <input
                            name="variantName"
                            defaultValue={variantEditing?.name ?? ""}
                            autoFocus
                            required
                          />
                        </label>

                        <VariantOptionsEditor
                          key={variantEditorMode === "CREATE" ? "create" : variantEditing?.id ?? "edit"}
                          initialJson={variantEditing?.optionValuesJson ?? "{}"}
                        />

                        <div className={styles.formGrid}>
                          <label className={styles.field}>
                            <span>SKU de variante</span>
                            <input
                              name="variantSku"
                              defaultValue={variantEditing?.sku ?? ""}
                              required
                            />
                          </label>

                          <label className={styles.field}>
                            <span>Stock físico inicial</span>
                            <input
                              name="variantOnHand"
                              type="number"
                              min="0"
                              step="1"
                              defaultValue={variantEditing?.onHand ?? 0}
                              required
                            />
                          </label>
                        </div>

                        <label className={styles.field}>
                          <span>Umbral de reposición</span>
                          <input
                            name="variantReorderThreshold"
                            type="number"
                            min="0"
                            step="1"
                            defaultValue={variantEditing?.reorderThreshold ?? 0}
                            required
                          />
                        </label>

                        <div className={styles.formGrid}>
                          <label className={styles.checkField}>
                            <input
                              name="variantTrackingEnabled"
                              type="checkbox"
                              defaultChecked={variantEditing ? variantEditing.trackingEnabled : true}
                            />
                            <span>Seguimiento activo</span>
                          </label>

                          <label className={styles.checkField}>
                            <input
                              name="variantActive"
                              type="checkbox"
                              defaultChecked={variantEditing ? variantEditing.active : true}
                            />
                            <span>Variante activa</span>
                          </label>
                        </div>

                        <label className={styles.field}>
                          <span>Nota</span>
                          <input name="variantNote" />
                        </label>

                        {mutationError && <p className={styles.dialogError} role="alert">{mutationError}</p>}

                        <div className={styles.dialogActions}>
                          <button
                            className="button ghost"
                            type="button"
                            disabled={mutationPending}
                            onClick={() => {
                              setVariantEditorMode(null);
                              setVariantEditing(null);
                              setMutationError("");
                            }}
                          >
                            Cancelar
                          </button>
                          <button className="button primary" type="submit" disabled={mutationPending}>
                            {mutationPending
                              ? "Guardando…"
                              : variantEditorMode === "CREATE"
                                ? "Crear variante"
                                : "Guardar variante"}
                          </button>
                        </div>
                      </form>
                    )}

                    {variantAdjusting && (
                      <form className={styles.variantForm} onSubmit={handleVariantAdjustment}>
                        <h3>Ajustar · {variantAdjusting.name}</h3>
                        <p className={styles.dialogHint}>
                          Disponible ahora: <strong>{variantAdjusting.available}</strong>
                        </p>

                        <label className={styles.field}>
                          <span>Ajuste de variante</span>
                          <input name="variantDelta" type="number" step="1" required autoFocus />
                        </label>

                        <label className={styles.field}>
                          <span>Nota de ajuste</span>
                          <input name="variantAdjustmentNote" />
                        </label>

                        {mutationError && <p className={styles.dialogError} role="alert">{mutationError}</p>}

                        <div className={styles.dialogActions}>
                          <button
                            className="button ghost"
                            type="button"
                            disabled={mutationPending}
                            onClick={() => {
                              setVariantAdjusting(null);
                              setMutationError("");
                            }}
                          >
                            Cancelar
                          </button>
                          <button className="button primary" type="submit" disabled={mutationPending}>
                            {mutationPending ? "Aplicando…" : "Aplicar ajuste de variante"}
                          </button>
                        </div>
                      </form>
                    )}

                    {variants.length === 0 ? (
                      <p className={styles.dialogHint}>Este producto todavía no tiene variantes.</p>
                    ) : (
                      <div className={styles.variantList}>
                        {variants.map(variant => (
                          <article
                            key={variant.id}
                            className={styles.variantCard}
                            data-testid={`inventory-variant-${variant.id}`}
                          >
                            <div className={styles.variantCardMain}>
                              <div>
                                <strong>{variant.name}</strong>
                                <span>{variant.sku}</span>
                              </div>
                              <span className={variant.active ? styles.statusOk : styles.statusMuted}>
                                {variant.active ? "Activa" : "Inactiva"}
                              </span>
                            </div>

                            <div className={styles.variantMetrics}>
                              <span>Disponible: <strong>{variant.available}</strong></span>
                              <span>Físico: <strong>{variant.onHand}</strong></span>
                              <span>Reservado: <strong>{variant.reserved}</strong></span>
                            </div>

                            <div className={styles.variantActions}>
                              <button
                                className="button ghost"
                                type="button"
                                onClick={() => void openVariantHistory(variant)}
                              >
                                Historial
                              </button>

                              {canManageVariants && (
                                <>
                                  <button
                                    className="button ghost"
                                    type="button"
                                    onClick={() => {
                                      setMutationError("");
                                      setVariantAdjusting(null);
                                      setVariantEditing(variant);
                                      setVariantEditorMode("EDIT");
                                    }}
                                  >
                                    Editar
                                  </button>
                                  {variant.active && variant.trackingEnabled && (
                                    <button
                                      className="button secondary"
                                      type="button"
                                      onClick={() => {
                                        setMutationError("");
                                        setVariantEditorMode(null);
                                        setVariantEditing(null);
                                        setVariantAdjusting(variant);
                                      }}
                                    >
                                      Ajustar
                                    </button>
                                  )}
                                  {variant.active && (
                                    <button
                                      className="button ghost"
                                      type="button"
                                      disabled={mutationPending}
                                      onClick={() => void deactivateVariant(variant)}
                                    >
                                      Desactivar
                                    </button>
                                  )}
                                </>
                              )}
                            </div>
                          </article>
                        ))}
                      </div>
                    )}
                  </>
                )}
              </div>
            </section>
          </div>
        )}

        {variantHistoryTarget && (
          <div className={styles.dialogBackdrop}>
            <section
              className={styles.dialog}
              role="dialog"
              aria-modal="true"
              aria-labelledby="variantHistoryTitle"
            >
              <div className={styles.dialogHeader}>
                <div>
                  <span className={styles.dialogEyebrow}>Trazabilidad de variante</span>
                  <h2 id="variantHistoryTitle">Historial variante · {variantHistoryTarget.name}</h2>
                </div>
                <button
                  className="button ghost"
                  type="button"
                  onClick={() => setVariantHistoryTarget(null)}
                >
                  Cerrar historial
                </button>
              </div>

              <div className={styles.historyBody}>
                {variantHistoryPending && (
                  <p className={styles.dialogHint} role="status">Cargando movimientos…</p>
                )}
                {!variantHistoryPending && variantHistoryError && (
                  <p className={styles.dialogError} role="alert">{variantHistoryError}</p>
                )}
                {!variantHistoryPending && !variantHistoryError && variantHistory.length === 0 && (
                  <p className={styles.dialogHint}>Esta variante todavía no tiene movimientos.</p>
                )}
                {!variantHistoryPending && !variantHistoryError && variantHistory.length > 0 && (
                  <div className={styles.movementList}>
                    {variantHistory.map(movement => (
                      <article key={movement.id} className={styles.movementCard}>
                        <div className={styles.movementHeader}>
                          <strong>{movementLabel(movement.type)}</strong>
                          <time dateTime={movement.createdAt}>{movementDate(movement.createdAt)}</time>
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

        {inspectorTarget && (
          <ProductInspector
            row={rows.find(row => row.id === inspectorTarget.id) ?? inspectorTarget}
            canReadMedia={canReadMedia}
            canReadVariants={canReadVariants}
            canManageStock={model.canManageStock}
            onClose={() => setInspectorTarget(null)}
            onVariants={() => {
              const row = rows.find(value => value.id === inspectorTarget.id) ?? inspectorTarget;
              setInspectorTarget(null);
              void openVariants(row);
            }}
            onHistory={() => {
              const row = rows.find(value => value.id === inspectorTarget.id) ?? inspectorTarget;
              setInspectorTarget(null);
              void openHistory(row);
            }}
            onConfigure={() => {
              const row = rows.find(value => value.id === inspectorTarget.id) ?? inspectorTarget;
              setInspectorTarget(null);
              setMutationError("");
              setConfigureTarget(row);
            }}
            onAdjust={() => {
              const row = rows.find(value => value.id === inspectorTarget.id) ?? inspectorTarget;
              setInspectorTarget(null);
              setMutationError("");
              setAdjustTarget(row);
            }}
          />
        )}

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
                  <h2 id="createProductTitle">
                    {productEditing ? `Editar producto · ${productEditing.name || "Producto"}` : "Nuevo producto"}
                  </h2>
                </div>
                <button
                  className="button ghost"
                  type="button"
                  disabled={mutationPending}
                  onClick={() => {
                    setProductCreateOpen(false);
                    setProductEditing(null);
                  }}
                >
                  Cancelar
                </button>
              </div>

              <form className={styles.dialogForm} onSubmit={handleCreateProduct}>
                <label className={styles.field}>
                  <span>Nombre</span>
                  <input name="name" defaultValue={productEditing?.name ?? ""} autoFocus required />
                </label>

                <label className={styles.field}>
                  <span>Descripción</span>
                  <input name="description" defaultValue={productEditing?.description ?? ""} />
                </label>

                <div className={styles.formGrid}>
                  <label className={styles.field}>
                    <span>Precio</span>
                    <input
                      name="price"
                      type="number"
                      min="0"
                      step="0.01"
                      defaultValue={productEditing?.price ?? ""}
                      required
                    />
                  </label>

                  <label className={styles.field}>
                    <span>Moneda</span>
                    <input
                      name="currency"
                      defaultValue={productEditing?.currency ?? "CLP"}
                      maxLength={3}
                      required
                    />
                  </label>
                </div>

                <p className={styles.dialogHint}>
                  Paso 1: guarda los datos del catálogo. Si también gestionas stock, puedes continuar
                  al paso 2 sin buscar el producto otra vez. Son dos operaciones independientes.
                </p>

                {mutationError && <p className={styles.dialogError} role="alert">{mutationError}</p>}

                <div className={styles.dialogActions}>
                  <button className={productEditing ? "button primary" : "button secondary"}
                    type="submit" name="nextStep" value="catalog" disabled={mutationPending}>
                    {mutationPending
                      ? "Guardando…"
                      : productEditing
                        ? "Guardar producto"
                        : "Crear producto"}
                  </button>
                  {!productEditing && model.canManageStock && (
                    <button className="button primary" type="submit" name="nextStep"
                      value="configure" disabled={mutationPending}>
                      {mutationPending ? "Guardando…" : "Crear y configurar stock"}
                    </button>
                  )}
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
                  <h2 id="configureStockTitle">
                    {configureTarget.configured ? "Editar stock" : "Configurar stock"} · {configureTarget.name}
                  </h2>
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
                {!configureTarget.configured && (
                  <p className={styles.dialogHint}>
                    Paso 2: define SKU, cantidad física y mínimo de reposición.
                    Hasta guardar aquí, el producto sigue sin stock configurado.
                  </p>
                )}
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

                <label className={styles.checkField}>
                  <input
                    name="trackingEnabled"
                    type="checkbox"
                    defaultChecked={configureTarget.configured ? configureTarget.trackingEnabled : true}
                  />
                  <span>Seguimiento activo</span>
                </label>

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
          </>,
          document.querySelector('[data-react-app="recepvoz"]') ?? document.body
        )}
      </main>
    </AppShell>
  );
}
