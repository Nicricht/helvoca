export const TOKEN_KEY = "helvoca_access_token";

export class ApiError extends Error {
  readonly status: number;
  readonly payload: unknown;

  constructor(status: number, message: string, payload: unknown = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.payload = payload;
  }
}

export function getAccessToken(): string {
  return sessionStorage.getItem(TOKEN_KEY) ?? "";
}

export function clearAccessToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
}

async function readPayload(response: Response): Promise<unknown> {
  if (response.status === 204) return null;
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    try {
      return await response.json();
    } catch {
      return null;
    }
  }
  try {
    return await response.text();
  } catch {
    return null;
  }
}

function payloadMessage(payload: unknown): string | null {
  if (!payload || typeof payload !== "object") return null;
  const candidate = payload as Record<string, unknown>;
  const value = candidate.message ?? candidate.detail;
  return typeof value === "string" && value.trim() ? value : null;
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  authenticated = true
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");

  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const token = getAccessToken();
  if (authenticated && token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(path, { ...init, headers });
  const payload = await readPayload(response);

  if (response.status === 401) {
    clearAccessToken();
    window.location.replace("/");
    throw new ApiError(401, "La sesión expiró.", payload);
  }

  if (!response.ok) {
    throw new ApiError(
      response.status,
      payloadMessage(payload) ?? `HTTP ${response.status}`,
      payload
    );
  }

  return payload as T;
}
