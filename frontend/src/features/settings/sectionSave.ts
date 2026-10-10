import {
  createKnowledgeItem,
  createServiceItem,
  deactivateKnowledgeItem,
  deactivateServiceItem,
  replaceBusinessHours,
  saveAiAgent,
  saveBusinessProfile,
  updateBusiness,
  updateKnowledgeItem,
  updateServiceItem,
  type BusinessHour,
  type KnowledgeItem,
  type ServiceItem
} from "./api";
import type { SectionKey, SettingsDraft } from "./viewModel";

export const editedSections: SectionKey[] = ["business", "receptionist", "services", "hours", "knowledge"];

const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);
const nullable = (value: string) => value.trim() || null;

export function hourValues(hours: BusinessHour[]) {
  return hours.map(hour => ({
    dayOfWeek: Number(hour.dayOfWeek),
    openTime: String(hour.openTime).slice(0, 5),
    closeTime: String(hour.closeTime).slice(0, 5)
  }));
}

export function sectionChanged(section: SectionKey, draft: SettingsDraft, saved: SettingsDraft) {
  if (section === "business") return !same(
    [draft.businessName, draft.timezone, draft.language, draft.humanTransferPhone, draft.profile],
    [saved.businessName, saved.timezone, saved.language, saved.humanTransferPhone, saved.profile]
  );
  if (section === "receptionist") return !same(draft.agent, saved.agent);
  if (section === "services") return !same(draft.services, saved.services);
  if (section === "hours") return !same(hourValues(draft.hours), hourValues(saved.hours));
  if (section === "knowledge") return !same(draft.knowledge, saved.knowledge);
  return false;
}

export function validateSection(section: SectionKey, draft: SettingsDraft) {
  if (section === "business") {
    if (!draft.businessName.trim()) return "El nombre del negocio es obligatorio.";
    if (!draft.timezone.trim() || !draft.language.trim()) return "La zona horaria y el idioma son obligatorios.";
  }
  if (section === "receptionist" && !draft.agent.greeting.trim()) {
    return "Define el saludo inicial de la recepcionista IA.";
  }
  if (section === "services" && draft.services.some(item =>
    !item.name.trim() || !Number.isInteger(Number(item.durationMinutes))
    || Number(item.durationMinutes) <= 0
    || (item.price != null && (!Number.isFinite(Number(item.price)) || Number(item.price) < 0)))) {
    return "Revisa el nombre, la duración y el precio de los servicios antes de guardar.";
  }
  if (section === "knowledge" && draft.knowledge.some(item =>
    !item.title.trim() || !item.content.trim())) {
    return "Completa el título y la respuesta antes de guardar el conocimiento.";
  }
  if (section === "hours" && draft.hours.some(item =>
    !item.openTime || !item.closeTime || String(item.openTime).slice(0, 5) >= String(item.closeTime).slice(0, 5))) {
    return "Cada horario debe tener apertura anterior al cierre.";
  }
  return null;
}

export type PersistedChange = {
  patch: Partial<SettingsDraft>;
  created?: { section: "services"; index: number; item: ServiceItem }
    | { section: "knowledge"; index: number; item: KnowledgeItem };
};

/**
 * Write only the selected section. Successful steps are committed to the caller's
 * baseline immediately so a retry cannot replay them after a later failure.
 * No provider calls are triggered here.
 */
export async function persistSettingsSection(
  section: SectionKey,
  draft: SettingsDraft,
  baseline: () => SettingsDraft,
  committed: (value: PersistedChange) => void
) {
  if (section === "business") {
    const saved = baseline();
    const rootNew = [draft.businessName, draft.timezone, draft.language, draft.humanTransferPhone];
    const rootOld = [saved.businessName, saved.timezone, saved.language, saved.humanTransferPhone];
    if (!same(rootNew, rootOld)) {
      await updateBusiness({
        name: draft.businessName.trim(),
        timezone: draft.timezone.trim(),
        language: draft.language.trim(),
        humanTransferPhone: nullable(draft.humanTransferPhone)
      });
      committed({ patch: {
        businessName: draft.businessName,
        timezone: draft.timezone,
        language: draft.language,
        humanTransferPhone: draft.humanTransferPhone
      } });
    }
    if (!same(draft.profile, baseline().profile)) {
      await saveBusinessProfile({
        ...draft.profile,
        presetKey: nullable(String(draft.profile.presetKey ?? "")),
        publicDescription: nullable(String(draft.profile.publicDescription ?? "")),
        publicPhone: nullable(String(draft.profile.publicPhone ?? "")),
        publicEmail: nullable(String(draft.profile.publicEmail ?? "")),
        websiteUrl: nullable(String(draft.profile.websiteUrl ?? "")),
        addressLine: nullable(String(draft.profile.addressLine ?? "")),
        commune: nullable(String(draft.profile.commune ?? "")),
        city: nullable(String(draft.profile.city ?? "")),
        region: nullable(String(draft.profile.region ?? "")),
        countryCode: nullable(String(draft.profile.countryCode ?? "").toUpperCase()),
        defaultCurrency: String(draft.profile.defaultCurrency || "CLP").trim().toUpperCase()
      });
      committed({ patch: { profile: { ...draft.profile } } });
    }
    return;
  }

  if (section === "receptionist") {
    await saveAiAgent({
      ...draft.agent,
      name: draft.agent.name.trim(),
      language: draft.agent.language.trim(),
      voice: draft.agent.voice.trim(),
      greeting: draft.agent.greeting.trim(),
      instructions: draft.agent.instructions.trim(),
      capabilities: [...draft.agent.capabilities]
    });
    committed({ patch: { agent: { ...draft.agent, capabilities: [...draft.agent.capabilities] } } });
    return;
  }

  if (section === "hours") {
    await replaceBusinessHours(hourValues(draft.hours));
    committed({ patch: { hours: draft.hours.map(hour => ({ ...hour })) } });
    return;
  }

  if (section === "services") {
    for (const item of baseline().services) {
      if (item.id && !draft.services.some(value => value.id === item.id)) {
        await deactivateServiceItem(item.id);
        committed({ patch: { services: baseline().services.filter(value => value.id !== item.id) } });
      }
    }
    for (let index = 0; index < draft.services.length; index++) {
      const item = draft.services[index];
      const previous = item.id ? baseline().services.find(value => value.id === item.id) : null;
      if (previous && same(item, previous)) continue;
      const payload = {
        name: item.name.trim(),
        description: nullable(String(item.description ?? "")),
        durationMinutes: Number(item.durationMinutes),
        price: item.price == null ? null : Number(item.price),
        active: item.active !== false
      };
      const response = item.id
        ? await updateServiceItem(item.id, payload)
        : await createServiceItem(payload);
      if (!response.id) throw new Error("El servicio se guardó sin identificador. Recarga antes de reintentar.");
      const persisted: ServiceItem = { ...item, ...response };
      const items = previous
        ? baseline().services.map(value => value.id === item.id ? persisted : value)
        : [...baseline().services, persisted];
      committed({
        patch: { services: items },
        created: { section: "services", index, item: persisted }
      });
    }
    return;
  }

  if (section === "knowledge") {
    for (const item of baseline().knowledge) {
      if (item.id && !draft.knowledge.some(value => value.id === item.id)) {
        await deactivateKnowledgeItem(item.id);
        committed({ patch: { knowledge: baseline().knowledge.filter(value => value.id !== item.id) } });
      }
    }
    for (let index = 0; index < draft.knowledge.length; index++) {
      const item = draft.knowledge[index];
      const previous = item.id ? baseline().knowledge.find(value => value.id === item.id) : null;
      if (previous && same(item, previous)) continue;
      const payload = {
        title: item.title.trim(),
        category: nullable(String(item.category ?? "")),
        content: item.content.trim(),
        active: item.active !== false
      };
      const response = item.id
        ? await updateKnowledgeItem(item.id, payload)
        : await createKnowledgeItem(payload);
      if (!response.id) throw new Error("La respuesta se guardó sin identificador. Recarga antes de reintentar.");
      const persisted: KnowledgeItem = { ...item, ...response };
      const items = previous
        ? baseline().knowledge.map(value => value.id === item.id ? persisted : value)
        : [...baseline().knowledge, persisted];
      committed({
        patch: { knowledge: items },
        created: { section: "knowledge", index, item: persisted }
      });
    }
  }
}
