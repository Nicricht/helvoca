import {
  createKnowledgeItem,
  getBusinessHours,
  getKnowledge,
  replaceBusinessHours,
  type BusinessHour
} from "../settings/api";
import type { BusinessImportSetupSuggestion } from "./api";

export interface ReviewSetupSuggestion extends BusinessImportSetupSuggestion {
  selected: boolean;
}

const DAY: Record<string, number> = {
  MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4,
  FRIDAY: 5, SATURDAY: 6, SUNDAY: 7
};

export function setupReviewRows(items: BusinessImportSetupSuggestion[] = []): ReviewSetupSuggestion[] {
  return items.map(item => ({ ...item, selected: true }));
}

/** Explicit owner action only. Never creates orders, stock, calls or payments. */
export async function applyReviewedSetup(rows: ReviewSetupSuggestion[]): Promise<{
  faqCreated: number;
  faqSkipped: number;
  hourDaysReplaced: number;
}> {
  const chosen = rows.filter(item => item.selected);
  if (!chosen.length) throw new Error("Selecciona alguna sugerencia antes de guardar.");

  const selectedHours: BusinessHour[] = [];
  const selectedFaqs: Array<{ title: string; content: string }> = [];
  const seenDays = new Set<number>();
  const seenQuestions = new Set<string>();
  for (const item of chosen) {
    const key = item.key.trim();
    const value = item.value.trim();
    if (!key || !value) throw new Error("Completa las preguntas, respuestas y horarios seleccionados.");
    if (item.kind === "BUSINESS_HOURS") {
      const dayOfWeek = DAY[key];
      const match = /^((?:[01]\d|2[0-3]):[0-5]\d)-((?:[01]\d|2[0-3]):[0-5]\d)$/.exec(value);
      if (!dayOfWeek || !match || match[1] >= match[2] || seenDays.has(dayOfWeek)) {
        throw new Error("Horario inválido, repetido o con cierre anterior a apertura: " + key);
      }
      seenDays.add(dayOfWeek);
      selectedHours.push({ dayOfWeek, openTime: match[1], closeTime: match[2] });
    } else if (item.kind === "FAQ") {
      if (key.length > 200 || value.length > 4000) {
        throw new Error("FAQ demasiado extensa: " + key.slice(0, 80));
      }
      const normalized = key.toLocaleLowerCase("es-CL");
      if (seenQuestions.has(normalized)) {
        throw new Error("Pregunta duplicada entre sugerencias: " + key);
      }
      seenQuestions.add(normalized);
      selectedFaqs.push({ title: key, content: value });
    } else {
      throw new Error("Tipo de sugerencia desconocido.");
    }
  }

  // Read existing state BEFORE writing anything, so a read failure causes no mutation.
  const currentHours = selectedHours.length ? await getBusinessHours() : [];
  const existingFaqs = selectedFaqs.length ? await getKnowledge() : [];
  if (selectedHours.length) {
    // The endpoint replaces the entire schedule. Preserve ALL untouched days.
    const combined = currentHours.filter(hour => !seenDays.has(hour.dayOfWeek))
      .concat(selectedHours)
      .sort((a, b) => a.dayOfWeek - b.dayOfWeek ||
        a.openTime.localeCompare(b.openTime));
    await replaceBusinessHours(combined);
  }

  let faqCreated = 0;
  let faqSkipped = 0;
  for (const faq of selectedFaqs) {
    const found = existingFaqs.some(item =>
      item.title.trim().toLocaleLowerCase("es-CL") ===
      faq.title.toLocaleLowerCase("es-CL"));
    if (found) {
      // Existing questions are never silently overwritten by an import.
      faqSkipped++;
      continue;
    }
    await createKnowledgeItem({ title: faq.title, content: faq.content, category: "FAQ", active: true });
    faqCreated++;
  }
  return { faqCreated, faqSkipped, hourDaysReplaced: selectedHours.length };
}
