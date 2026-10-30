import { NextRequest, NextResponse } from "next/server";
import { errorMessage } from "@/lib/platform/errors";

export const dynamic = "force-dynamic";
const id = "[A-Za-z0-9_-]{1,64}";
const routes: [RegExp, string[]][] = [
  [/^jogadores$/, ["POST"]],
  [/^(?:jogadores|admin)\/sessoes$/, ["POST"]],
  [/^(?:jogadores|admin)\/sessoes\/atual$/, ["GET", "DELETE"]],
  [/^jogadores\/(?:recuperacao|redefinicao)$/, ["POST"]],
  [/^jogadores\/conta$/, ["DELETE"]],
  [/^jogadores\/(?:arenas|biblioteca)$/, ["GET"]],
  [new RegExp(`^jogadores/arenas/${id}/quadras$`), ["GET"]],
  [new RegExp(`^jogadores/quadras/${id}/(?:disponibilidade|lances)$`), ["GET"]],
  [new RegExp(`^jogadores/lances/${id}$`), ["GET"]],
  [new RegExp(`^jogadores/lances/${id}/acesso$`), ["POST"]],
  [new RegExp(`^jogadores/biblioteca/${id}$`), ["PUT", "DELETE"]],
  [/^admin\/empresas$/, ["GET", "POST"]],
  [new RegExp(`^admin/empresas/${id}$`), ["GET"]],
  [new RegExp(`^admin/empresas/${id}/status$`), ["PATCH"]],
  [new RegExp(`^admin/empresas/${id}/quadras$`), ["GET", "POST"]],
  [new RegExp(`^admin/empresas/${id}/quadras/${id}$`), ["GET"]],
  [new RegExp(`^admin/empresas/${id}/quadras/${id}/status$`), ["PATCH"]],
  [new RegExp(`^admin/empresas/${id}/quadras/${id}/dispositivos$`), ["GET", "POST"]],
  [new RegExp(`^admin/empresas/${id}/auditoria$`), ["GET"]],
  [new RegExp(`^admin/empresas/${id}/purga/(?:reservas|executar)$`), ["POST"]],
  [new RegExp(`^admin/empresas/${id}/reconciliacao$`), ["GET", "POST"]],
  [new RegExp(`^admin/dispositivos/${id}$`), ["GET"]],
  [new RegExp(`^admin/dispositivos/${id}/status$`), ["PATCH"]],
  [new RegExp(`^admin/dispositivos/${id}/credenciais/rotacao$`), ["POST"]],
  [new RegExp(`^admin/dispositivos/${id}/configuracoes$`), ["POST"]],
  [new RegExp(`^admin/dispositivos/${id}/saude$`), ["GET"]],
  [new RegExp(`^admin/lances/${id}/bloqueio$`), ["PATCH"]],
  [new RegExp(`^admin/auditoria/${id}$`), ["GET"]],
  [/^admin\/metricas$/, ["GET"]],
];
const publicPaths = new Set([
  "jogadores",
  "jogadores/sessoes",
  "jogadores/recuperacao",
  "jogadores/redefinicao",
  "admin/sessoes",
]);
const queryKeys = new Set(["page", "size", "data", "hora_inicio", "hora_fim"]);
type RouteContext = { params: Promise<{ path: string[] }> };

function error(status: number, code: string) {
  return NextResponse.json(
    { code, message: errorMessage(status, code) },
    { status, headers: { "Cache-Control": "no-store" } },
  );
}
function clear(response: NextResponse, cookie: string) {
  response.cookies.set(cookie, "", {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: 0,
  });
  return response;
}

function sameOrigin(request: NextRequest) {
  const value = request.headers.get("origin");
  if (!value) return false;
  try {
    const origin = new URL(value);
    // Next can normalize nextUrl.hostname to localhost in development. Host is
    // the browser's actual destination authority; forwarded hosts are ignored.
    const destination = request.headers.get("host") ?? request.nextUrl.host;
    return (
      origin.origin === value &&
      origin.host.toLowerCase() === destination.toLowerCase() &&
      origin.protocol === request.nextUrl.protocol
    );
  } catch {
    return false;
  }
}

export async function proxy(request: NextRequest, context: RouteContext) {
  const segments = (await context.params).path;
  if (!segments?.length || segments.some((segment) => !new RegExp(`^${id}$`).test(segment)))
    return error(404, "RESOURCE_NOT_FOUND");
  const path = segments.join("/"),
    method = request.method;
  if (!routes.some(([pattern, methods]) => pattern.test(path) && methods.includes(method)))
    return error(404, "RESOURCE_NOT_FOUND");
  const role = segments[0] === "admin" ? "admin" : "player",
    cookie = `clicklance_${role}_session`;
  const login = method === "POST" && (path === "jogadores/sessoes" || path === "admin/sessoes");
  const logout =
    method === "DELETE" && (path.endsWith("/sessoes/atual") || path === "jogadores/conta");
  if (method !== "GET") {
    if (!sameOrigin(request)) return error(403, "FORBIDDEN");
    if (
      request.headers.get("content-type")?.split(";", 1)[0].trim().toLowerCase() !==
      "application/json"
    )
      return error(415, "INVALID_REQUEST");
  }
  const authenticated = !(method === "POST" && publicPaths.has(path));
  const token = request.cookies.get(cookie)?.value;
  if (authenticated && !token) return error(401, "UNAUTHENTICATED");
  let backend: URL;
  try {
    backend = new URL(process.env.BACKEND_URL ?? "http://127.0.0.1:8080");
    if (
      !["http:", "https:"].includes(backend.protocol) ||
      backend.username ||
      backend.password ||
      backend.search ||
      backend.hash
    )
      throw new Error();
    backend.pathname = `${backend.pathname.replace(/\/$/, "")}/api/v1/${path}`;
    for (const [key, value] of request.nextUrl.searchParams)
      if (queryKeys.has(key)) backend.searchParams.append(key, value);
  } catch {
    return error(503, "DEPENDENCY_UNAVAILABLE");
  }
  const headers: Record<string, string> = { Accept: "application/json" };
  if (authenticated) headers.Authorization = `Bearer ${token}`;
  let body: string | undefined;
  if (method !== "GET") {
    headers["Content-Type"] = "application/json";
    if (Number(request.headers.get("content-length") ?? 0) > 1048576)
      return error(413, "INVALID_REQUEST");
    const chunks: string[] = [];
    let bytes = 0;
    const reader = request.body?.getReader();
    const decoder = new TextDecoder();
    try {
      if (reader) {
        while (true) {
          const chunk = await reader.read();
          if (chunk.done) break;
          bytes += chunk.value.byteLength;
          if (bytes > 1048576) {
            await reader.cancel();
            return error(413, "INVALID_REQUEST");
          }
          chunks.push(decoder.decode(chunk.value, { stream: true }));
        }
        chunks.push(decoder.decode());
      }
    } catch {
      return error(400, "INVALID_REQUEST");
    }
    body = chunks.join("");
    if (body) {
      try {
        JSON.parse(body);
      } catch {
        return error(400, "INVALID_REQUEST");
      }
    } else body = undefined;
  }
  let upstream: Response;
  try {
    upstream = await fetch(backend, {
      method,
      headers,
      body,
      cache: "no-store",
      redirect: "manual",
      signal: AbortSignal.timeout(20000),
    });
  } catch {
    const response = error(503, "DEPENDENCY_UNAVAILABLE");
    return logout ? clear(response, cookie) : response;
  }
  if (upstream.status >= 300 && upstream.status < 400) return error(502, "DEPENDENCY_UNAVAILABLE");
  if (!upstream.ok) {
    let payload: { code?: unknown; correlation_id?: unknown; violations?: unknown } = {};
    try {
      payload = await upstream.json();
    } catch {
      /* Only safe fallback codes leave the proxy. */
    }
    const code =
      typeof payload.code === "string" && /^[A-Z_]{1,64}$/.test(payload.code)
        ? payload.code
        : "REQUEST_FAILED";
    const correlation =
      typeof payload.correlation_id === "string" && /^[a-f0-9-]{36}$/i.test(payload.correlation_id)
        ? payload.correlation_id
        : undefined;
    const violations = Array.isArray(payload.violations)
      ? payload.violations
          .filter(
            (v): v is { field: string; code: string } =>
              typeof v?.field === "string" &&
              /^[A-Za-z0-9_.]{1,80}$/.test(v.field) &&
              typeof v?.code === "string" &&
              /^[A-Z_]{1,64}$/.test(v.code),
          )
          .map(({ field, code }) => ({ field, code }))
      : [];
    const safe = NextResponse.json(
      {
        code,
        message: errorMessage(upstream.status, code),
        ...(correlation ? { correlation_id: correlation } : {}),
        ...(violations.length ? { violations } : {}),
      },
      { status: upstream.status, headers: { "Cache-Control": "no-store" } },
    );
    const retry = upstream.headers.get("retry-after");
    if (retry && /^\d{1,6}$/.test(retry)) safe.headers.set("Retry-After", retry);
    if (correlation) safe.headers.set("X-Correlation-ID", correlation);
    // Preserve a still-valid credential on transient logout/closure failures so
    // the user can retry the operation and the backend can revoke it durably.
    return upstream.status === 401 ? clear(safe, cookie) : safe;
  }
  if (login) {
    let payload: { access_token?: unknown; expires_in?: unknown } = {};
    try {
      payload = await upstream.json();
    } catch {
      return error(502, "DEPENDENCY_UNAVAILABLE");
    }
    if (
      typeof payload.access_token !== "string" ||
      !/^[A-Za-z0-9_.-]{1,8192}$/.test(payload.access_token) ||
      payload.expires_in !== 3600
    )
      return error(502, "DEPENDENCY_UNAVAILABLE");
    const response = NextResponse.json(
      { authenticated: true },
      { status: 201, headers: { "Cache-Control": "no-store" } },
    );
    response.cookies.set(cookie, payload.access_token, {
      httpOnly: true,
      sameSite: "lax",
      secure: process.env.NODE_ENV === "production",
      path: "/",
      maxAge: 3600,
    });
    return response;
  }
  if (upstream.status === 204) {
    const response = new NextResponse(null, {
      status: 204,
      headers: { "Cache-Control": "no-store" },
    });
    return logout ? clear(response, cookie) : response;
  }
  let payload: unknown;
  try {
    payload = await upstream.json();
  } catch {
    return error(502, "DEPENDENCY_UNAVAILABLE");
  }
  return NextResponse.json(payload, {
    status: upstream.status,
    headers: { "Cache-Control": "no-store" },
  });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
