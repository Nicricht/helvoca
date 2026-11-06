import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { AlertTriangle } from "lucide-react";
import { extractFreeSetup, type LocalSetupRow } from "../../features/businessImport/localSetup";
import { applyReviewedSetup } from "../../features/businessImport/setupApply";
import styles from "./BusinessImportPage.module.css";

/**
 * An intentionally separate, no-paid-AI workflow. The parent imports products,
 * services and document facts as before; no existing feature is overwritten.
 */
export function FreeSetupReview({ files }: { files: File[] }) {
  const queryClient = useQueryClient();
  const [rows, setRows] = useState<LocalSetupRow[]>([]);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    void extractFreeSetup(files).then(result => {
      if (!mounted) return;
      setRows(result.rows);
      setWarnings(result.warnings);
    }).catch(() => {
      if (mounted) setWarnings(["No pudimos leer la planilla de preguntas u horarios."]);
    }).finally(() => {
      if (mounted) setLoading(false);
    });
    return () => { mounted = false; };
  }, [files]);

  function update(index: number, patch: Partial<LocalSetupRow>) {
    setRows(current => current.map((item, i) => i === index ? { ...item, ...patch } : item));
    setMessage("");
  }

  async function approve() {
    if (saving || !rows.some(row => row.selected)) return;
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const result = await applyReviewedSetup(rows);
      setRows(current => current.map(row => ({ ...row, selected: false })));
      void queryClient.invalidateQueries({ queryKey: ["settings"] });
      setMessage("Configuración aprobada: " + result.faqCreated + " FAQ creada(s), " +
        result.faqSkipped + " FAQ existente(s) sin sobrescribir, " +
        result.hourDaysReplaced + " día(s) de horario guardados.");
    } catch (cause) {
      const reason = cause instanceof Error ? cause.message : "No se pudo guardar la configuración.";
      setError(reason + " Comprueba lo guardado antes de reintentar si hubo un error de red.");
    } finally {
      setSaving(false);
    }
  }

  if (loading || (!rows.length && !warnings.length)) return null;
  return (
    <section className={styles.card} data-testid="free-setup-review" aria-label="Preguntas y horarios CSV TSV gratis">
      <div className={styles.stepHead}>
        <span>2</span>
        <div>
          <strong>Preguntas frecuentes y horarios desde tu planilla</strong>
          <small>Sin IA pagada. Selecciona y revisa antes de aprobar.</small>
        </div>
      </div>
      <p>Solo se extraen filas explícitas de CSV o TSV. No se inventan respuestas, horarios ni disponibilidad.
        Excel y PDF siguen su propio flujo de importación. Los demás días y las FAQ existentes se conservan.</p>
      {warnings.length > 0 && (
        <div className={styles.warningList} data-testid="free-setup-warning">
          {warnings.map((warning, index) => (
            <div key={warning + index}><AlertTriangle size={15} aria-hidden="true" /><span>{warning}</span></div>
          ))}
        </div>
      )}
      {rows.length > 0 && (
        <div className={styles.rows}>
          {rows.map((row, index) => (
            <article key={row.sourceName + "-" + row.sourceRow + "-" + index}
                     className={styles.importRow} data-testid={"free-setup-row-" + index}>
              <div className={styles.rowTop}>
                <label className={styles.selectItem}>
                  <input type="checkbox" checked={row.selected}
                         aria-label={"Seleccionar sugerencia " + index}
                         onChange={event => update(index, { selected: event.target.checked })} />
                  <span>{row.kind === "FAQ" ? "Pregunta frecuente" : "Horario semanal"}</span>
                </label>
                <span className={styles.confidence}>Revisión obligatoria</span>
              </div>
              <div className={styles.rowGrid}>
                <label className={styles.field}>
                  <span>{row.kind === "FAQ" ? "Pregunta" : "Día"}</span>
                  <input aria-label={"Clave sugerida " + index} value={row.key}
                         readOnly={row.kind === "BUSINESS_HOURS"} maxLength={200}
                         onChange={event => update(index, { key: event.target.value })} />
                </label>
                <label className={styles.field}>
                  <span>{row.kind === "FAQ" ? "Respuesta" : "Apertura-cierre HH:MM-HH:MM"}</span>
                  <input aria-label={"Respuesta sugerida " + index} value={row.value}
                         maxLength={row.kind === "FAQ" ? 4000 : 11}
                         onChange={event => update(index, { value: event.target.value })} />
                </label>
              </div>
              <div className={styles.rowSource}>
                <span>Fuente</span><strong>{row.sourceName} · fila {row.sourceRow}</strong>
              </div>
            </article>
          ))}
        </div>
      )}
      {message && <p role="status">{message}</p>}
      {error && <p role="alert">{error}</p>}
      <div className={styles.applyBar}>
        <span>Aprueba FAQ y horarios por separado. No activa canales ni genera cobros, citas o ventas.</span>
        <button type="button" className="button secondary"
                disabled={saving || !rows.some(row => row.selected)}
                onClick={() => void approve()}>
          {saving ? "Guardando sugerencias…" : "Aprobar sugerencias seleccionadas"}
        </button>
      </div>
    </section>
  );
}
