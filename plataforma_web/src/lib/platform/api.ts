import { ApiError, errorMessage } from "./errors";
import { demoRequest, enterDemoSession } from "./demo";
import type {
  Arena,
  Availability,
  Court,
  MediaAccess,
  Page,
  Replay,
  ReplayFilters,
  RequestOptions,
  Role,
  User,
  Violation,
} from "./types";

export { ApiError } from "./errors";
export { ApiError as PlatformError } from "./errors";
export const isDemo = process.env.NEXT_PUBLIC_API_MODE !== "http";

export function sessionChanged(role: Role, reason: "updated" | "expired" = "updated") {
  if (typeof window !== "undefined")
    window.dispatchEvent(new CustomEvent("clicklance:session", { detail: { role, reason } }));
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const role = options.role ?? (path.startsWith("/admin/") ? "admin" : "player");
  try {
    if (isDemo) return await demoRequest<T>(path, { ...options, role });
    const headers: Record<string, string> = { Accept: "application/json" };
    const method = options.method ?? "GET";
    if (method !== "GET") headers["Content-Type"] = "application/json";
    let response: Response;
    try {
      response = await fetch(`/api/backend${path}`, {
        method,
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        credentials: "same-origin",
        cache: "no-store",
        redirect: "error",
      });
    } catch {
      throw new ApiError("Sem conexão com o servidor. Confira a rede e tente novamente.");
    }
    if (!response.ok) {
      let payload: { code?: string; correlation_id?: string; violations?: Violation[] } = {};
      try {
        payload = await response.json();
      } catch {
        /* Safe status-based fallback. */
      }
      const retry = Number(response.headers.get("Retry-After"));
      throw new ApiError(
        errorMessage(response.status, payload.code),
        response.status,
        payload.code ?? "REQUEST_FAILED",
        payload.correlation_id,
        payload.violations ?? [],
        retry > 0 ? retry : undefined,
      );
    }
    if (response.status === 204) return undefined as T;
    return (await response.json()) as T;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) sessionChanged(role, "expired");
    throw error;
  }
}

function query(params: Record<string, string | number | undefined>) {
  const result = new URLSearchParams();
  for (const [key, value] of Object.entries(params))
    if (value !== undefined && value !== "") result.set(key, String(value));
  return result.size ? `?${result}` : "";
}
const id = encodeURIComponent;
const player = "/jogadores";

export const platform = {
  arenas: (page = 0, size = 20) => request<Page<Arena>>(`${player}/arenas${query({ page, size })}`),
  courts: (company: string, page = 0, size = 20) =>
    request<Page<Court>>(`${player}/arenas/${id(company)}/quadras${query({ page, size })}`),
  availability: (court: string, data?: string) =>
    request<Availability>(`${player}/quadras/${id(court)}/disponibilidade${query({ data })}`),
  replays: (court: string, filters: ReplayFilters = {}) =>
    request<Page<Replay>>(`${player}/quadras/${id(court)}/lances${query({ ...filters })}`),
  replay: (event: string) => request<Replay>(`${player}/lances/${id(event)}`),
  access: (event: string, download = false) =>
    request<MediaAccess>(`${player}/lances/${id(event)}/acesso`, {
      method: "POST",
      body: { download },
    }),
  library: (page = 0, size = 20) =>
    request<Page<Replay>>(`${player}/biblioteca${query({ page, size })}`),
  save: (event: string) => request<Replay>(`${player}/biblioteca/${id(event)}`, { method: "PUT" }),
  remove: (event: string) =>
    request<void>(`${player}/biblioteca/${id(event)}`, { method: "DELETE" }),
  register: (email: string, password: string) =>
    request<User>(player, { method: "POST", body: { email, password } }),
  recover: (email: string) =>
    request<{ message: string }>(`${player}/recuperacao`, { method: "POST", body: { email } }),
  reset: (token: string, password: string) =>
    request<void>(`${player}/redefinicao`, { method: "POST", body: { token, password } }),
  closeAccount: async () => {
    await request<void>(`${player}/conta`, { method: "DELETE" });
    sessionChanged("player");
  },
  login: async (email: string, password: string, role: Role = "player") => {
    await request<{ authenticated: boolean }>(
      `/${role === "admin" ? "admin" : "jogadores"}/sessoes`,
      { method: "POST", body: { email, password }, role },
    );
    sessionChanged(role);
    return request<User>(`/${role === "admin" ? "admin" : "jogadores"}/sessoes/atual`, { role });
  },
  current: (role: Role = "player") =>
    request<User>(`/${role === "admin" ? "admin" : "jogadores"}/sessoes/atual`, { role }),
  logout: async (role: Role = "player") => {
    await request<void>(`/${role === "admin" ? "admin" : "jogadores"}/sessoes/atual`, {
      method: "DELETE",
      role,
    });
    sessionChanged(role);
  },
  enterDemo: async (role: Role = "player") => {
    if (!isDemo)
      throw new ApiError("A demonstração não está disponível nesta conexão.", 403, "FORBIDDEN");
    const user = enterDemoSession(role);
    sessionChanged(role);
    return user;
  },
};

/** Signed media must never become a javascript/data URL or an insecure remote request. */
export function safeMediaUrl(value: string): string | null {
  try {
    const url = new URL(value);
    if (url.username || url.password) return null;
    if (url.protocol === "https:") return url.toString();
    if (
      process.env.NODE_ENV !== "production" &&
      url.protocol === "http:" &&
      ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)
    )
      return url.toString();
  } catch {
    /* Invalid media reference. */
  }
  return null;
}
