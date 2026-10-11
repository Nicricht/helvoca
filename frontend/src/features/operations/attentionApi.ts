import { ApiError, apiRequest } from "../../api/client";
import type { HumanAttentionItem } from "./api";

/**
 * Home read-only source of truth. A malformed response is never an empty inbox.
 * Kept separate from unrelated audit, export and request-history APIs so Home
 * source coverage measures exactly the affected API behavior.
 */
export async function getHumanAttention(): Promise<HumanAttentionItem[]> {
  const payload = await apiRequest<unknown>("/api/v1/operations/attention");
  if (!Array.isArray(payload) || !payload.every(item =>
    item != null
      && typeof item === "object"
      && (item.kind === "REQUEST" || item.kind === "HANDOFF")
      && typeof item.id === "string" && item.id.length > 0
      && (item.operationId == null || typeof item.operationId === "string")
      && typeof item.title === "string"
      && typeof item.priority === "string"
      && typeof item.status === "string"
      && typeof item.createdAt === "string"
  )) {
    throw new ApiError(502, "No pudimos verificar los pendientes.");
  }
  return payload as HumanAttentionItem[];
}
