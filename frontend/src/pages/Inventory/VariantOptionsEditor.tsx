import { useState } from "react";
import styles from "./InventoryPage.module.css";

interface OptionRow {
  id: number;
  key: string;
  value: string;
  protected: boolean;
}

function readInitialOptions(raw: string): { rows: OptionRow[]; invalid: boolean } {
  try {
    const parsed: unknown = JSON.parse(raw || "{}");
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return { rows: [], invalid: true };
    }
    return {
      rows: Object.entries(parsed).map(([key, value], index) => ({
        id: index,
        key,
        value: value === null || typeof value === "object" ? "" : String(value),
        protected: value === null || typeof value === "object"
      })),
      invalid: false
    };
  } catch {
    return { rows: [], invalid: true };
  }
}

/**
 * The API stores optionValuesJson; the customer edits ordinary labeled fields.
 * Nested legacy option values remain protected and are copied unchanged.
 */
export function VariantOptionsEditor({ initialJson }: { initialJson: string }) {
  const initial = readInitialOptions(initialJson);
  const [rows, setRows] = useState<OptionRow[]>(initial.rows);
  const [nextId, setNextId] = useState(initial.rows.length);

  function change(id: number, part: "key" | "value", value: string) {
    setRows(current => current.map(row => row.id === id ? { ...row, [part]: value } : row));
  }

  return (
    <fieldset className={styles.variantOptions}>
      <legend>Características de la variante</legend>
      <p className={styles.dialogHint}>
        Agrega atributos como color, talla, presentación o material. No necesitas escribir código.
      </p>
      <input type="hidden" name="variantOptionsBaseline" value={initialJson || "{}"} />
      <input type="hidden" name="variantOptionsInvalid" value={initial.invalid ? "true" : "false"} />
      {initial.invalid && (
        <p role="alert" className={styles.dialogError}>
          Las características guardadas tienen un formato no compatible. No se modificarán desde este editor.
        </p>
      )}
      {!initial.invalid && rows.map((row, index) => row.protected ? (
        <div className={styles.variantOptionProtected} key={row.id}>
          <strong>{row.key}</strong>
          <span>Valor avanzado guardado: se conservará sin cambios.</span>
        </div>
      ) : (
        <div className={styles.variantOptionRow} key={row.id}>
          <label className={styles.field}>
            <span>Característica {index + 1}</span>
            <input
              name="variantOptionKey"
              value={row.key}
              onChange={event => change(row.id, "key", event.target.value)}
              placeholder="Ej. Color"
              maxLength={80}
              disabled={initial.invalid}
            />
          </label>
          <label className={styles.field}>
            <span>Valor</span>
            <input
              name="variantOptionValue"
              value={row.value}
              onChange={event => change(row.id, "value", event.target.value)}
              placeholder="Ej. Azul"
              maxLength={180}
              disabled={initial.invalid}
            />
          </label>
          <button className="button ghost" type="button" disabled={initial.invalid}
            aria-label={`Quitar característica ${row.key || index + 1}`}
            onClick={() => setRows(current => current.filter(item => item.id !== row.id))}>
            Quitar
          </button>
        </div>
      ))}
      <button className="button secondary" type="button" disabled={initial.invalid}
        onClick={() => {
          setRows(current => [...current, { id: nextId, key: "", value: "", protected: false }]);
          setNextId(current => current + 1);
        }}>
        Agregar característica
      </button>
    </fieldset>
  );
}

/** Validate the visible form and preserve any non-editable legacy values. */
export function serializeVariantOptions(form: HTMLFormElement): string {
  const data = new FormData(form);
  if (data.get("variantOptionsInvalid") === "true") {
    throw new Error("Las características existentes no tienen un formato compatible. No se guardaron cambios.");
  }
  const baselineRaw = String(data.get("variantOptionsBaseline") ?? "{}");
  let baseline: Record<string, unknown>;
  try {
    const value: unknown = JSON.parse(baselineRaw);
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("invalid");
    baseline = value as Record<string, unknown>;
  } catch {
    throw new Error("No pudimos interpretar las características guardadas. No se guardaron cambios.");
  }

  const values = Object.create(null) as Record<string, unknown>;
  for (const [key, value] of Object.entries(baseline)) {
    if (value === null || typeof value === "object") values[key] = value;
  }
  const keys = data.getAll("variantOptionKey").map(value => String(value).trim());
  const entries = data.getAll("variantOptionValue").map(value => String(value).trim());
  if (keys.length !== entries.length) throw new Error("Revisa las características de la variante.");

  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    const value = entries[i];
    if (!key && !value) continue;
    if (!key || !value) throw new Error("Cada característica debe tener un nombre y un valor.");
    if (Object.hasOwn(values, key)) {
      throw new Error(`La característica "${key}" está repetida o reservada.`);
    }
    const original = baseline[key];
    values[key] = typeof original !== "object" && original !== undefined
      && String(original) === value ? original : value;
  }
  return JSON.stringify(values);
}
