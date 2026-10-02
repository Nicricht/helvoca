import { ApiError, clearAccessToken, getAccessToken, apiRequest } from "../../api/client";

export type RequestStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CANCELLED";

export interface BusinessRequest {
  id: string;
  customerId?: string | null;
  callId?: string | null;
  requestType?: string | null;
  title?: string | null;
  description?: string | null;
  contactName?: string | null;
  contactPhone?: string | null;
  priority?: string | null;
  status: RequestStatus;
  source?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AuditEntry {
  id: string;
  action?: string | null;
  resourceType?: string | null;
  resourceId?: string | null;
  result?: string | null;
  actorType?: string | null;
  actorName?: string | null;
  actorEmail?: string | null;
  actorRole?: string | null;
  beforeState?: Record<string, unknown> | null;
  afterState?: Record<string, unknown> | null;
  createdAt?: string | null;
}

export interface AuditFilters {
  actor?: string;
  action?: string;
  resourceType?: string;
  from?: string;
  to?: string;
}

function query(filters: AuditFilters = {}) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value) params.set(key, value);
  }
  return params.toString();
}

export function getRequests() {
  return apiRequest<BusinessRequest[]>("/api/v1/requests");
}

export function updateRequestStatus(id: string, status: RequestStatus) {
  return apiRequest<BusinessRequest>(
    "/api/v1/requests/" + encodeURIComponent(id) + "/status",
    {
      method: "PATCH",
      body: JSON.stringify({ status })
    }
  );
}

export function getAudit(filters: AuditFilters = {}) {
  const suffix = query(filters);
  return apiRequest<AuditEntry[]>("/api/v1/audit" + (suffix ? "?" + suffix : ""));
}

async function downloadAuthenticated(path: string, fallbackName: string) {
  const token = getAccessToken();
  const response = await fetch(path, {
    headers: token ? { Authorization: "Bearer " + token } : undefined
  });

  if (response.status === 401) {
    clearAccessToken();
    window.location.replace("/");
    throw new ApiError(401, "La sesión expiró.");
  }
  if (!response.ok) {
    throw new ApiError(response.status, "No pudimos preparar la descarga.");
  }

  const blob = await response.blob();
  const disposition = response.headers.get("content-disposition") || "";
  const filename = disposition.match(/filename="?([^";]+)"?/i)?.[1] || fallbackName;
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function exportCustomers(format: "csv" | "xlsx") {
  return downloadAuthenticated(
    "/api/v1/customers/export?format=" + format,
    "helvoca-clientes." + format
  );
}

export function exportAudit(format: "csv" | "xlsx", filters: AuditFilters = {}) {
  const params = new URLSearchParams({ format });
  const filterQuery = query(filters);
  if (filterQuery) {
    const extra = new URLSearchParams(filterQuery);
    extra.forEach((value, key) => params.set(key, value));
  }
  return downloadAuthenticated(
    "/api/v1/audit/export?" + params.toString(),
    "helvoca-auditoria." + format
  );
}
