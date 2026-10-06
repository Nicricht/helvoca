import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, Navigate } from "react-router-dom";
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  FileSpreadsheet,
  FileText,
  Image,
  PackageCheck,
  RotateCcw,
  ShieldCheck,
  Sparkles,
  UploadCloud
} from "lucide-react";
import { AppShell } from "../../components/AppShell/AppShell";
import { getCurrentUser } from "../../features/dashboard/api";
import { getBusiness } from "../../features/settings/api";
import {
  applyBusinessImport,
  previewBusinessImport,
  type BusinessImportApplyItem,
  type BusinessImportApplyResult,
  type BusinessImportPreview,
  type ImportItemKind
} from "../../features/businessImport/api";
import styles from "./BusinessImportPage.module.css";

type EditableRow = {
  selected: boolean;
  name: string;
  description: string;
  price: string;
  currency: string;
  sku: string;
  onHand: string;
  category: string;
  kind: ImportItemKind;
  durationMinutes: string;
  confidence: number;
  sourceName: string;
};

function fileKind(file: File) {
  const name = file.name.toLowerCase();
  if (/\.(xlsx?|csv|tsv)$/.test(name)) return "Planilla";
  if (name.endsWith(".pdf")) return "PDF";
  if (file.type.startsWith("image/")) return "Imagen";
  return "Archivo";
}

function fileIcon(file: File) {
  const kind = fileKind(file);
  if (kind === "Planilla") return FileSpreadsheet;
  if (kind === "Imagen") return Image;
  return FileText;
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}

function sourceLabel(kind?: string | null) {
  const labels: Record<string, string> = {
    PRODUCTS: "Productos",
    SERVICES: "Servicios",
    SALES: "Ventas históricas",
    RECEIPTS: "Boletas / documentos",
    CUSTOMERS: "Clientes",
    MIXED: "Datos mixtos",
    UNKNOWN: "Sin clasificar"
  };
  return kind ? labels[kind] ?? kind : "Sin clasificar";
}

function rowsFromPreview(preview: BusinessImportPreview): EditableRow[] {
  return (preview.products ?? []).map(item => ({
    selected: true,
    name: item.name ?? "",
    description: item.description ?? "",
    price: item.price === null || item.price === undefined ? "" : String(item.price),
    currency: item.currency || "CLP",
    sku: item.kind === "SERVICE" ? "" : item.sku ?? "",
    onHand: item.kind === "SERVICE" || item.onHand === null || item.onHand === undefined ? "" : String(item.onHand),
    category: item.category ?? "",
    kind: item.kind === "SERVICE" ? "SERVICE" : "PRODUCT",
    durationMinutes:
      item.kind === "SERVICE" && item.durationMinutes !== null && item.durationMinutes !== undefined
        ? String(item.durationMinutes)
        : "",
    confidence: Math.round(Number(item.confidence ?? 0) * 100),
    sourceName: item.sourceName ?? ""
  }));
}

function optionalNumber(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : Number.NaN;
}

function buildApplyPayload(rows: EditableRow[]): BusinessImportApplyItem[] {
  return rows.filter(row => row.selected).map(row => {
    const name = row.name.trim();
    const price = optionalNumber(row.price);
    const service = row.kind === "SERVICE";
    const stock = service ? null : optionalNumber(row.onHand);
    const duration = service ? optionalNumber(row.durationMinutes) : null;

    if (!name) throw new Error("Todos los elementos seleccionados necesitan nombre.");
    if (Number.isNaN(price)) throw new Error("Precio inválido en " + name + ".");
    if (!service && (Number.isNaN(stock) || (stock !== null && !Number.isInteger(stock)))) {
      throw new Error("El stock de " + name + " debe ser un entero igual o mayor que cero.");
    }
    if (service && (duration === null || Number.isNaN(duration) || !Number.isInteger(duration) || duration <= 0)) {
      throw new Error("La duración de " + name + " debe ser un número entero de minutos mayor que cero.");
    }

    return {
      name,
      description: row.description.trim() || null,
      price,
      currency: row.currency.trim() || "CLP",
      sku: service ? null : row.sku.trim() || null,
      onHand: service ? null : stock,
      category: row.category.trim() || null,
      kind: row.kind,
      durationMinutes: service ? duration : null,
      sourceName: row.sourceName || null
    };
  });
}

export function BusinessImportPage() {
  const prefilledBusiness = useRef(false);
  const mutationLock = useRef(false);
  const [businessName, setBusinessName] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const [preview, setPreview] = useState<BusinessImportPreview | null>(null);
  const [rows, setRows] = useState<EditableRow[]>([]);
  const [result, setResult] = useState<BusinessImportApplyResult | null>(null);
  const [previewPending, setPreviewPending] = useState(false);
  const [applyPending, setApplyPending] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const me = useQuery({
    queryKey: ["auth", "me"],
    queryFn: getCurrentUser,
    retry: false
  });

  const admin = Boolean(me.data?.roles?.includes("BUSINESS_ADMIN"));

  const business = useQuery({
    queryKey: ["settings", "business"],
    queryFn: getBusiness,
    retry: false,
    enabled: admin
  });

  useEffect(() => {
    const name = String(business.data?.name || "").trim();
    if (!prefilledBusiness.current && name) {
      prefilledBusiness.current = true;
      setBusinessName(name);
    }
  }, [business.data?.name]);

  const selectedCount = useMemo(() => rows.filter(row => row.selected).length, [rows]);

  if (!me.isPending && me.data && !admin) {
    return <Navigate to="/settings" replace />;
  }

  if (me.isPending || !admin) return null;

  function setSelectedFiles(next: File[]) {
    const accepted = next.filter(file => file.size > 0).slice(0, 12);
    setFiles(accepted);
    setPreview(null);
    setRows([]);
    setResult(null);
    setMessage("");
    setError("");
  }

  function removeFile(index: number) {
    setSelectedFiles(files.filter((_, itemIndex) => itemIndex !== index));
  }

  function updateRow(index: number, patch: Partial<EditableRow>) {
    setRows(current => current.map((row, itemIndex) => {
      if (itemIndex !== index) return row;
      const next = { ...row, ...patch };
      if (patch.kind === "SERVICE") {
        next.sku = "";
        next.onHand = "";
      }
      if (patch.kind === "PRODUCT") {
        next.durationMinutes = "";
      }
      return next;
    }));
  }

  async function analyze() {
    if (mutationLock.current || previewPending) return;
    const name = businessName.trim();
    if (!name) {
      setError("Escribe el nombre del negocio antes de analizar.");
      return;
    }
    if (!files.length) {
      setError("Selecciona al menos un archivo.");
      return;
    }

    mutationLock.current = true;
    setPreviewPending(true);
    setError("");
    setMessage("");
    setResult(null);
    try {
      const next = await previewBusinessImport(name, files);
      setPreview(next);
      setRows(rowsFromPreview(next));
      setMessage("Borrador creado. Revisa cada dato antes de aplicar.");
    } catch (value) {
      setError(value instanceof Error ? value.message : "No pudimos crear el borrador.");
    } finally {
      mutationLock.current = false;
      setPreviewPending(false);
    }
  }

  async function apply() {
    if (mutationLock.current || applyPending) return;
    let payload: BusinessImportApplyItem[];
    try {
      payload = buildApplyPayload(rows);
    } catch (value) {
      setError(value instanceof Error ? value.message : "Revisa el borrador antes de aplicar.");
      return;
    }
    if (!payload.length) {
      setError("Selecciona al menos un producto o servicio.");
      return;
    }

    mutationLock.current = true;
    setApplyPending(true);
    setError("");
    setMessage("");
    try {
      const next = await applyBusinessImport(payload);
      setResult(next);
      setMessage("Datos del negocio guardados correctamente.");
    } catch (value) {
      setError(value instanceof Error ? value.message : "No pudimos aplicar la importación.");
    } finally {
      mutationLock.current = false;
      setApplyPending(false);
    }
  }

  function reset() {
    setFiles([]);
    setPreview(null);
    setRows([]);
    setResult(null);
    setMessage("");
    setError("");
  }

  return (
    <AppShell>
      <main className={"rv-page-frame " + styles.page} data-visual-page="settings-import">
        <header className={styles.header}>
          <div>
            <Link className={styles.backLink} to="/settings">
              <ArrowLeft size={15} aria-hidden="true" /> Configuración
            </Link>
            <p className="eyebrow">ONBOARDING EXPRESS</p>
            <h1>Importar negocio</h1>
            <p>
              Convierte planillas, PDF o imágenes que el negocio ya usa en un borrador revisable para RecepVoz.
            </p>
          </div>
          <div className={styles.headerSignal}>
            <ShieldCheck size={17} aria-hidden="true" />
            <span><strong>Control humano</strong> Nada se guarda antes de confirmar.</span>
          </div>
        </header>

        <section className={styles.safetyStrip} aria-label="Flujo seguro de importación">
          <Sparkles size={18} aria-hidden="true" />
          <strong>Vista previa → revisión → aplicar</strong>
          <span>Las planillas claras evitan IA. PDF e imágenes pueden usar análisis semántico, siempre antes del guardado.</span>
        </section>

        {(error || message) && (
          <div className={error ? styles.errorBanner : styles.successBanner} role={error ? "alert" : "status"}>
            {error ? <AlertTriangle size={16} aria-hidden="true" /> : <CheckCircle2 size={16} aria-hidden="true" />}
            <span>{error || message}</span>
          </div>
        )}

        <section className={styles.intakeGrid}>
          <article className={styles.card}>
            <div className={styles.stepHead}>
              <span>1</span>
              <div><strong>Material del negocio</strong><small>Hasta 12 archivos, 10 MB por archivo</small></div>
            </div>

            <label className={styles.field}>
              <span>Nombre del negocio</span>
              <input
                value={businessName}
                onChange={event => setBusinessName(event.target.value)}
                maxLength={150}
                autoComplete="organization"
                placeholder="Veterinaria Norte"
              />
            </label>

            <label
              className={styles.dropzone}
              onDragOver={event => event.preventDefault()}
              onDrop={event => {
                event.preventDefault();
                setSelectedFiles(Array.from(event.dataTransfer.files));
              }}
            >
              <input
                aria-label="Archivos del negocio"
                type="file"
                multiple
                accept=".csv,.tsv,.xls,.xlsx,.pdf,image/jpeg,image/png,image/webp"
                onChange={event => setSelectedFiles(Array.from(event.target.files ?? []))}
              />
              <UploadCloud size={30} aria-hidden="true" />
              <strong>Arrastra archivos o selecciónalos</strong>
              <span>Excel, CSV, PDF, JPG, PNG o WEBP</span>
            </label>

            {files.length > 0 && (
              <div className={styles.fileList}>
                {files.map((file, index) => {
                  const Icon = fileIcon(file);
                  return (
                    <article key={file.name + "-" + index} className={styles.fileChip}>
                      <Icon size={17} aria-hidden="true" />
                      <span><strong>{file.name}</strong><small>{fileKind(file)} · {formatBytes(file.size)}</small></span>
                      <button type="button" onClick={() => removeFile(index)} aria-label={"Quitar " + file.name}>×</button>
                    </article>
                  );
                })}
              </div>
            )}

            <div className={styles.costNote}>
              <Sparkles size={16} aria-hidden="true" />
              <span><strong>Modo ahorro automático</strong> Las planillas reconocibles se procesan sin IA.</span>
            </div>

            <button
              className="button primary"
              type="button"
              disabled={!files.length || !businessName.trim() || previewPending}
              onClick={analyze}
            >
              {previewPending ? "Analizando…" : "Analizar y crear borrador"}
            </button>
          </article>

          <article className={styles.card}>
            <div className={styles.stepHead}>
              <span>?</span>
              <div><strong>¿Qué puede entender?</strong><small>No necesitas preparar un formato especial</small></div>
            </div>
            <div className={styles.guideList}>
              <div><FileSpreadsheet size={18} aria-hidden="true" /><span><strong>Excel / CSV</strong> Productos, servicios, precios, duración, SKU y stock.</span></div>
              <div><Image size={18} aria-hidden="true" /><span><strong>Fotos</strong> Menús, carteles, vitrinas y listas de precios legibles.</span></div>
              <div><FileText size={18} aria-hidden="true" /><span><strong>PDF</strong> Menús, catálogos, tarifarios y documentos del negocio.</span></div>
            </div>
            <div className={styles.historyNote}>
              <strong>Ventas y boletas históricas</strong>
              <p>Se reconocen como contexto histórico. Esta importación nunca las transforma en pedidos, cobros o pagos reales.</p>
            </div>
          </article>
        </section>

        {preview && (
          <section className={styles.card}>
            <div className={styles.reviewHeader}>
              <div className={styles.stepHead}>
                <span>2</span>
                <div><strong>Revisa el borrador</strong><small>Solo se guardará lo que dejes seleccionado</small></div>
              </div>
              <div className={styles.reviewActions}>
                <span>{rows.length} elemento{rows.length === 1 ? "" : "s"}</span>
                <button
                  className="button ghost"
                  type="button"
                  onClick={() => {
                    const select = rows.some(row => !row.selected);
                    setRows(current => current.map(row => ({ ...row, selected: select })));
                  }}
                >
                  {rows.some(row => !row.selected) ? "Seleccionar todos" : "Deseleccionar todos"}
                </button>
              </div>
            </div>

            <div className={styles.sourceGrid}>
              {(preview.sources ?? []).map((source, index) => (
                <article key={source.name + "-" + index} className={styles.sourceCard} data-recognized={source.recognized ? "true" : "false"}>
                  <div><strong>{source.name}</strong><span>{sourceLabel(source.kind)} · {source.rowCount || 0} filas</span></div>
                  <b>{source.method === "SPREADSHEET" ? "Automático" : source.method === "AI" ? "IA" : "Revisar"}</b>
                </article>
              ))}
            </div>

            {(preview.warnings ?? []).length > 0 && (
              <div className={styles.warningList}>
                {(preview.warnings ?? []).map((warning, index) => (
                  <div key={warning + "-" + index}><AlertTriangle size={15} aria-hidden="true" /><span>{warning}</span></div>
                ))}
              </div>
            )}

            {rows.length === 0 ? (
              <div className={styles.emptyState}>
                <strong>No hay productos o servicios listos para aplicar.</strong>
                <span>Cambia los archivos o revisa las advertencias detectadas.</span>
              </div>
            ) : (
              <div className={styles.rows}>
                {rows.map((row, index) => {
                  const service = row.kind === "SERVICE";
                  return (
                    <article
                      key={String(index)}
                      className={styles.importRow}
                      data-testid={"business-import-row-" + index}
                    >
                      <div className={styles.rowTop}>
                        <label className={styles.selectItem}>
                          <input
                            type="checkbox"
                            checked={row.selected}
                            onChange={event => updateRow(index, { selected: event.target.checked })}
                          />
                          <span>Importar</span>
                        </label>
                        <span className={styles.confidence}>{row.confidence}% confianza</span>
                      </div>

                      <div className={styles.rowGrid}>
                        <label className={styles.field}>
                          <span>Tipo</span>
                          <select
                            aria-label="Tipo"
                            value={row.kind}
                            onChange={event => updateRow(index, { kind: event.target.value === "SERVICE" ? "SERVICE" : "PRODUCT" })}
                          >
                            <option value="PRODUCT">Producto</option>
                            <option value="SERVICE">Servicio</option>
                          </select>
                        </label>
                        <label className={styles.field + " " + styles.nameField}>
                          <span>Nombre</span>
                          <input aria-label="Nombre" value={row.name} onChange={event => updateRow(index, { name: event.target.value })} maxLength={150} />
                        </label>
                        <label className={styles.field}>
                          <span>Categoría</span>
                          <input aria-label="Categoría" value={row.category} onChange={event => updateRow(index, { category: event.target.value })} maxLength={120} />
                        </label>
                        <label className={styles.field}>
                          <span>Duración</span>
                          <input
                            aria-label="Duración"
                            type="number"
                            min="1"
                            step="1"
                            disabled={!service}
                            value={service ? row.durationMinutes : ""}
                            onChange={event => updateRow(index, { durationMinutes: event.target.value })}
                            placeholder={service ? "minutos" : "No aplica"}
                          />
                        </label>
                        <label className={styles.field}>
                          <span>SKU</span>
                          <input
                            aria-label="SKU"
                            disabled={service}
                            value={service ? "" : row.sku}
                            onChange={event => updateRow(index, { sku: event.target.value })}
                            maxLength={80}
                            placeholder={service ? "No aplica" : "Opcional"}
                          />
                        </label>
                        <label className={styles.field}>
                          <span>Precio</span>
                          <input
                            aria-label="Precio"
                            type="number"
                            min="0"
                            step="0.01"
                            value={row.price}
                            onChange={event => updateRow(index, { price: event.target.value })}
                            placeholder="Sin dato"
                          />
                        </label>
                        <label className={styles.field}>
                          <span>Stock</span>
                          <input
                            aria-label="Stock"
                            type="number"
                            min="0"
                            step="1"
                            disabled={service}
                            value={service ? "" : row.onHand}
                            onChange={event => updateRow(index, { onHand: event.target.value })}
                            placeholder={service ? "No aplica" : "Sin dato"}
                          />
                        </label>
                        <label className={styles.field + " " + styles.descriptionField}>
                          <span>Descripción</span>
                          <input aria-label="Descripción" value={row.description} onChange={event => updateRow(index, { description: event.target.value })} maxLength={500} />
                        </label>
                      </div>

                      <div className={styles.rowSource}>
                        <span>Origen</span>
                        <strong>{row.sourceName || "archivo"}</strong>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}

            <div className={styles.applyBar}>
              <div>
                <strong>{selectedCount} seleccionado{selectedCount === 1 ? "" : "s"}</strong>
                <span>Stock vacío significa “sin dato”. Los servicios requieren duración y nunca usan inventario.</span>
              </div>
              <button
                className="button primary"
                type="button"
                disabled={selectedCount === 0 || applyPending}
                onClick={apply}
              >
                {applyPending ? "Importando…" : "Importar al negocio"}
              </button>
            </div>
          </section>
        )}

        {result && (
          <section className={styles.resultCard} aria-labelledby="businessImportResultTitle">
            <div className={styles.resultTitle}>
              <PackageCheck size={24} aria-hidden="true" />
              <div><h2 id="businessImportResultTitle">Importación aplicada</h2><p>RecepVoz ya usa los datos confirmados como verdad del negocio.</p></div>
            </div>
            <div className={styles.resultGrid}>
              <article><span>Nuevos</span><strong data-testid="business-import-created">{result.created ?? 0}</strong></article>
              <article><span>Actualizados</span><strong data-testid="business-import-updated">{result.updated ?? 0}</strong></article>
              <article><span>Inventario</span><strong data-testid="business-import-inventory">{result.inventoryConfigured ?? 0}</strong></article>
            </div>
            <div className={styles.resultActions}>
              <Link className="button primary" to="/inventory">Ver inventario</Link>
              <button className="button ghost" type="button" onClick={reset}>
                <RotateCcw size={15} aria-hidden="true" /> Importar otro archivo
              </button>
            </div>
          </section>
        )}
      </main>
    </AppShell>
  );
}
