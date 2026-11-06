import {
  createKnowledgeItem,
  getBusinessHours,
  getKnowledge,
  patchSelectedBusinessHours,
  type BusinessHour
} from "../settings/api";
import type { LocalSetupRow } from "./localSetup";

const DAY: Record<string, number> = {
  MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4,
  FRIDAY: 5, SATURDAY: 6, SUNDAY: 7
};
const HOUR_RANGE = /^((?:[01]\d|2[0-3]):[0-5]\d)-((?:[01]\d|2[0-3]):[0-5]\d)$/;

export type SetupSaveResult = {
  faqCreated: number;
  faqSkipped: number;
  hourDaysReplaced: number;
};

/** Call ONLY after the business admin explicitly approves selected rows. */
export async function applyReviewedSetup(rows: LocalSetupRow[]): Promise<SetupSaveResult> {
  const chosen = rows.filter(item => item.selected);
  if (!chosen.length) throw new Error("Selecciona alguna sugerencia antes de guardar.");

  const types = new Set(chosen.map(item => item.kind));
  // These are separate REST transaction boundaries; never claim cross-section atomicity.
  if (types.size !== 1) {
    throw new Error("Aprueba las FAQ y los horarios por separado para evitar guardados parciales.");
  }

  const selectedHours: BusinessHour[] = [];
  const selectedFaqs: Array<{ title: string; content: string }> = [];
  const days = new Set<number>();
  const questions = new Set<string>();

  for (const item of chosen) {
    const key = item.key.trim();
    const value = item.value.trim();
    if (!key || !value) throw new Error("Completa los datos seleccionados antes de guardarlos.");

    if (item.kind === "BUSINESS_HOURS") {
      const day = DAY[key];
      const match = HOUR_RANGE.exec(value);
      if (!day || !match || match[1] >= match[2] || days.has(day)) {
        throw new Error("Horario inválido, duplicado o con cierre anterior a apertura: " + key);
      }
      days.add(day);
      selectedHours.push({ dayOfWeek: day, openTime: match[1], closeTime: match[2] });
    } else if (item.kind === "FAQ") {
      if (key.length > 200 || value.length > 4000) {
        throw new Error("FAQ demasiado extensa: " + key.slice(0, 80));
      }
      const normalized = key.toLocaleLowerCase("es-CL");
      if (questions.has(normalized)) {
        throw new Error("Pregunta duplicada: " + key);
      }
      questions.add(normalized);
      selectedFaqs.push({ title: key, content: value });
    } else {
      throw new Error("Tipo de sugerencia no permitido.");
    }
  }

  // Read current tenant state before *any* mutation.
  const currentHours = selectedHours.length ? await getBusinessHours() : [];
  const existingFaqs = selectedFaqs.length ? await getKnowledge() : [];

  if (selectedHours.length) {
    // The server compares the selected-day snapshot under a tenant row lock.
    // Other administrators' changes to unrelated days are never replaced.
    const expectedSelectedDayHours = currentHours.filter(hour => days.has(hour.dayOfWeek));
    await patchSelectedBusinessHours(selectedHours, expectedSelectedDayHours);
  }

  let faqCreated = 0;
  let faqSkipped = 0;
  for (const faq of selectedFaqs) {
    const existing = existingFaqs.some(item =>
      item.title.trim().toLocaleLowerCase("es-CL") === faq.title.toLocaleLowerCase("es-CL"));
    if (existing) {
      faqSkipped++;
      continue;
    }
    await createKnowledgeItem({ title: faq.title, content: faq.content, category: "FAQ", active: true });
    faqCreated++;
  }

  return { faqCreated, faqSkipped, hourDaysReplaced: selectedHours.length };
}
