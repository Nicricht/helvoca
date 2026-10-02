import type {
  AiAgentInput,
  BusinessHour,
  BusinessProfileInput,
  KnowledgeItem,
  ServiceItem
} from "./api";

export type SectionKey =
  | "business"
  | "receptionist"
  | "services"
  | "hours"
  | "knowledge"
  | "channels"
  | "integrations"
  | "team";

export type SavePhase = "clean" | "dirty" | "saving" | "saved";

export interface SettingsDraft {
  businessName: string;
  timezone: string;
  language: string;
  humanTransferPhone: string;
  profile: BusinessProfileInput;
  services: ServiceItem[];
  hours: BusinessHour[];
  knowledge: KnowledgeItem[];
  agent: AiAgentInput;
}
