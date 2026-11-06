/**
 * Free, deterministic CSV/TSV onboarding helper. Nothing in this module writes
 * business data or invokes a provider. Spreadsheet Excel parsing stays on the
 * existing server path; this browser feature deliberately does not parse XLS/XLSX.
 */
export type LocalSetupKind = "FAQ" | "BUSINESS_HOURS";

export type LocalSetupRow = {
  kind: LocalSetupKind;
  key: string;
  value: string;
  sourceName: string;
  sourceRow: number;
  selected: boolean;
};

export type LocalSetupResult = { rows: LocalSetupRow[]; warnings: string[] };

const MAX_TEXT_BYTES = 1024 * 1024;
const MAX_ROWS_PER_FILE = 500;
const MAX_SUGGESTIONS = 100;

const QUESTION = new Set(["pregunta", "question", "consulta frecuente"]);
const ANSWER = new Set(["respuesta", "answer", "solucion"]);
const DAY = new Set(["dia", "dia semana", "day", "day of week"]);
const OPEN = new Set(["apertura", "hora apertura", "open time", "abre"]);
const CLOSE = new Set(["cierre", "hora cierre", "close time", "cierra"]);
const WEEKDAYS: Record<string, string> = {
  lunes: "MONDAY", monday: "MONDAY",
  martes: "TUESDAY", tuesday: "TUESDAY",
  miercoles: "WEDNESDAY", wednesday: "WEDNESDAY",
  jueves: "THURSDAY", thursday: "THURSDAY",
  viernes: "FRIDAY", friday: "FRIDAY",
  sabado: "SATURDAY", saturday: "SATURDAY",
  domingo: "SUNDAY", sunday: "SUNDAY"
};
const VALID_TIME = /^(?:[01]\d|2[0-3]):[0-5]\d$/;

function normalize(value: string): string {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLocaleLowerCase("es-CL").trim();
}

function firstColumn(headers: string[], names: Set<string>): number {
  return headers.findIndex(value => names.has(normalize(value)));
}

/** Return physical source line for each record, including quoted line breaks. */
function parseRecords(text: string, separator: string): Array<{ cells: string[]; sourceRow: number }> {
  const result: Array<{ cells: string[]; sourceRow: number }> = [];
  let cells: string[] = [];
  let cell = "";
  let inQuotes = false;
  let line = 1;
  let sourceRow = 1;
  const finishRecord = () => {
    cells.push(cell.trim());
    if (cells.some(value => value !== "")) {
      result.push({ cells, sourceRow });
      if (result.length > MAX_ROWS_PER_FILE + 1) throw new Error("demasiadas filas");
    }
    cells = [];
    cell = "";
    sourceRow = line + 1;
  };
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (char === '"') {
      if (inQuotes && text[i + 1] === '"') { cell += '"'; i++; }
      else inQuotes = !inQuotes;
    } else if (char === separator && !inQuotes) {
      cells.push(cell.trim());
      cell = "";
    } else if ((char === "\n" || char === "\r") && !inQuotes) {
      if (char === "\r" && text[i + 1] === "\n") i++;
      finishRecord();
      line++;
    } else {
      if (char === "\n") line++;
      cell += char;
    }
  }
  if (inQuotes) throw new Error("comillas sin cerrar");
  if (cell !== "" || cells.length) finishRecord();
  return result;
}

function separatorFor(header: string, filename: string): string {
  if (filename.toLowerCase().endsWith(".tsv")) return "\t";
  const choices = [";", "\t", ","];
  return choices.reduce((best, token) =>
    header.split(token).length > header.split(best).length ? token : best, ",");
}

function recordsFor(text: string, filename: string) {
  const cleaned = text.replace(/^\uFEFF/, "");
  const header = cleaned.split(/\r?\n/, 1)[0];
  return parseRecords(cleaned, separatorFor(header, filename));
}

function rowValue(cells: string[], index: number): string {
  // Column indices are validated by faq/hours header detection before this helper is called.
  return (cells[index] ?? "").trim();
}

export async function extractFreeSetup(files: File[]): Promise<LocalSetupResult> {
  const warnings: string[] = [];
  const candidates: LocalSetupRow[] = [];
  for (const file of files) {
    if (!/\.(?:csv|tsv)$/i.test(file.name)) continue;
    if (!file.size || file.size > MAX_TEXT_BYTES) {
      warnings.push(file.name + ": los datos de FAQ/horarios superan el límite gratuito de 1 MB o están vacíos.");
      continue;
    }
    let records: ReturnType<typeof recordsFor>;
    try { records = recordsFor(await file.text(), file.name); }
    catch {
      warnings.push(file.name + ": el CSV/TSV tiene comillas sin cerrar o demasiadas filas; revisa el archivo.");
      continue;
    }
    if (!records.length) continue;
    const headers = records[0].cells;
    const question = firstColumn(headers, QUESTION);
    const answer = firstColumn(headers, ANSWER);
    const day = firstColumn(headers, DAY);
    const opening = firstColumn(headers, OPEN);
    const closing = firstColumn(headers, CLOSE);
    const faq = question >= 0 && answer >= 0;
    const hours = day >= 0 && opening >= 0 && closing >= 0;
    if (faq === hours) continue; // not a recognized section, or ambiguous mixed format

    for (const record of records.slice(1, MAX_ROWS_PER_FILE + 1)) {
      const cells = record.cells;
      let key = "";
      let value = "";
      let kind: LocalSetupKind;
      if (faq) {
        kind = "FAQ";
        key = rowValue(cells, question);
        value = rowValue(cells, answer);
        if (!key || !value || key.length > 200 || value.length > 4000) {
          warnings.push(file.name + " fila " + record.sourceRow + ": pregunta/respuesta incompleta o demasiado larga.");
          continue;
        }
      } else {
        kind = "BUSINESS_HOURS";
        key = WEEKDAYS[normalize(rowValue(cells, day))] ?? "";
        const openTime = rowValue(cells, opening);
        const closeTime = rowValue(cells, closing);
        if (!key || !VALID_TIME.test(openTime) || !VALID_TIME.test(closeTime) || openTime >= closeTime) {
          warnings.push(file.name + " fila " + record.sourceRow + ": horario incompleto o inválido.");
          continue;
        }
        value = openTime + "-" + closeTime;
      }
      // Prevent spreadsheet formula injection from persisting as receptionist text.
      if (/^[=+@]/.test(key) || /^[=+@]/.test(value)) {
        warnings.push(file.name + " fila " + record.sourceRow + ": posible fórmula, no se propondrá.");
        continue;
      }
      candidates.push({ kind, key, value, sourceName: file.name, sourceRow: record.sourceRow, selected: false });
    }
  }

  const rows: LocalSetupRow[] = [];
  const byKey = new Map<string, LocalSetupRow>();
  const conflicted = new Set<string>();
  for (const item of candidates) {
    const identity = item.kind + ":" + normalize(item.key);
    if (conflicted.has(identity)) continue;
    const prior = byKey.get(identity);
    if (prior && prior.value !== item.value) {
      conflicted.add(identity);
      byKey.delete(identity);
      warnings.push("Conflicto en " + item.key + ": existen valores diferentes. Revisa el archivo antes de aprobar.");
    } else if (!prior) {
      byKey.set(identity, item);
    }
  }
  for (const item of byKey.values()) {
    if (rows.length === MAX_SUGGESTIONS) {
      warnings.push("La revisión gratuita se limitó a 100 sugerencias por lote.");
      break;
    }
    rows.push(item);
  }
  return { rows, warnings };
}
