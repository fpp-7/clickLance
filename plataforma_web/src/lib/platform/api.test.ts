import { afterEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { proxy } from "@/app/api/backend/[...path]/route";
import { demoRequest, enterDemoSession } from "./demo";
import { ApiError } from "./errors";
import { safeMediaUrl } from "./api";
import type { Arena, Page, Replay, User } from "./types";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});
const origin = "http://localhost:3000";
function incoming(
  path: string,
  method = "GET",
  headers: Record<string, string> = {},
  body?: unknown,
) {
  return new NextRequest(`${origin}/api/backend${path}`, {
    method,
    headers: {
      ...(method !== "GET" ? { "content-type": "application/json", origin } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}
const context = (path: string) => ({
  params: Promise.resolve({ path: path.split("/").filter(Boolean) }),
});

describe("same-origin backend proxy", () => {
  it("preserves the session cookie when logout fails before durable revocation", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(Response.json({ code: "DEPENDENCY_UNAVAILABLE" }, { status: 503 })),
    );
    const response = await proxy(
      incoming("/jogadores/sessoes/atual", "DELETE", {
        cookie: "clicklance_player_session=fixture-token",
      }),
      context("jogadores/sessoes/atual"),
    );
    expect(response.status).toBe(503);
    expect(response.headers.get("set-cookie")).toBeNull();
  });
  it("uses the actual Host when Next normalizes a loopback URL, without trusting forwarded hosts", async () => {
    const upstream = vi
      .fn()
      .mockResolvedValue(
        Response.json(
          { usuario_id: "fixture", email: "synthetic@example.invalid" },
          { status: 201 },
        ),
      );
    vi.stubGlobal("fetch", upstream);
    const valid = incoming(
      "/jogadores",
      "POST",
      { host: "127.0.0.1:3000", origin: "http://127.0.0.1:3000" },
      {},
    );
    expect((await proxy(valid, context("jogadores"))).status).toBe(201);
    const forged = incoming(
      "/jogadores",
      "POST",
      { host: "127.0.0.1:3000", origin, "x-forwarded-host": "localhost:3000" },
      {},
    );
    expect((await proxy(forged, context("jogadores"))).status).toBe(403);
    expect(upstream).toHaveBeenCalledTimes(1);
  });
  it("keeps login tokens in an HttpOnly role cookie and outside response JSON", async () => {
    const upstream = vi
      .fn()
      .mockResolvedValue(
        Response.json(
          { access_token: "fixture.jwt.signature", expires_in: 3600, token_type: "Bearer" },
          { status: 201 },
        ),
      );
    vi.stubGlobal("fetch", upstream);
    const response = await proxy(
      incoming(
        "/jogadores/sessoes",
        "POST",
        {},
        { email: "synthetic@example.invalid", password: "fixture-password" },
      ),
      context("jogadores/sessoes"),
    );
    expect(response.status).toBe(201);
    expect(await response.json()).toEqual({ authenticated: true });
    expect(response.headers.get("set-cookie")).toContain(
      "clicklance_player_session=fixture.jwt.signature",
    );
    expect(response.headers.get("set-cookie")).toContain("HttpOnly");
    expect(response.headers.get("set-cookie")).toContain("SameSite=lax");
    expect(upstream.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });
  it("rejects cross-origin and non-JSON mutations before contacting the backend", async () => {
    const upstream = vi.fn();
    vi.stubGlobal("fetch", upstream);
    const cross = await proxy(
      incoming("/jogadores/sessoes", "POST", { origin: "https://attacker.invalid" }, {}),
      context("jogadores/sessoes"),
    );
    expect(cross.status).toBe(403);
    const form = await proxy(
      incoming("/jogadores/sessoes", "POST", { "content-type": "text/plain" }, {}),
      context("jogadores/sessoes"),
    );
    expect(form.status).toBe(415);
    expect(upstream).not.toHaveBeenCalled();
  });
  it("injects only the matching cookie and never forwards arbitrary Authorization or query fields", async () => {
    const upstream = vi
      .fn()
      .mockResolvedValue(Response.json({ items: [], page: 0, size: 20, total: 0 }));
    vi.stubGlobal("fetch", upstream);
    const path = "/jogadores/arenas";
    const request = new NextRequest(`${origin}/api/backend${path}?page=1&token=must-not-forward`, {
      headers: {
        cookie: "clicklance_player_session=player.fixture; clicklance_admin_session=admin.fixture",
        Authorization: "Bearer forged",
      },
    });
    expect((await proxy(request, context(path))).status).toBe(200);
    expect(String(upstream.mock.calls[0][0])).toMatch(/\/api\/v1\/jogadores\/arenas\?page=1$/);
    expect(upstream.mock.calls[0][1].headers.Authorization).toBe("Bearer player.fixture");
    const denied = await proxy(
      incoming("/admin/empresas", "GET", { cookie: "clicklance_player_session=player.fixture" }),
      context("admin/empresas"),
    );
    expect(denied.status).toBe(401);
    expect(upstream).toHaveBeenCalledTimes(1);
  });
  it("does not allow unlisted paths and strips sensitive error extensions", async () => {
    const upstream = vi.fn().mockResolvedValue(
      Response.json(
        {
          code: "VALIDATION_FAILED",
          message: "secret fixture",
          correlation_id: "3c7bac19-2f96-4ba9-9216-59187eaf0048",
          password: "never",
          violations: [{ field: "email", code: "INVALID_FORMAT", rejected_value: "never" }],
        },
        { status: 422 },
      ),
    );
    vi.stubGlobal("fetch", upstream);
    expect((await proxy(incoming("/internal/config"), context("internal/config"))).status).toBe(
      404,
    );
    expect(upstream).not.toHaveBeenCalled();
    const response = await proxy(incoming("/jogadores", "POST", {}, {}), context("jogadores"));
    expect(response.status).toBe(422);
    expect(await response.json()).toEqual({
      code: "VALIDATION_FAILED",
      message: expect.any(String),
      correlation_id: "3c7bac19-2f96-4ba9-9216-59187eaf0048",
      violations: [{ field: "email", code: "INVALID_FORMAT" }],
    });
  });
  it("clears only the expired role and handles a 204 logout without JSON parsing", async () => {
    const upstream = vi
      .fn()
      .mockResolvedValueOnce(Response.json({ code: "UNAUTHENTICATED" }, { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", upstream);
    const expired = await proxy(
      incoming("/admin/sessoes/atual", "GET", { cookie: "clicklance_admin_session=admin.fixture" }),
      context("admin/sessoes/atual"),
    );
    expect(expired.status).toBe(401);
    expect(expired.headers.get("set-cookie")).toContain("clicklance_admin_session=;");
    expect(expired.headers.get("set-cookie")).not.toContain("clicklance_player_session");
    const logout = await proxy(
      incoming("/jogadores/sessoes/atual", "DELETE", {
        cookie: "clicklance_player_session=player.fixture",
      }),
      context("jogadores/sessoes/atual"),
    );
    expect(logout.status).toBe(204);
    expect(await logout.text()).toBe("");
    expect(logout.headers.get("set-cookie")).toContain("Max-Age=0");
  });
  it("returns a safe dependency failure without following a backend redirect", async () => {
    const upstream = vi
      .fn()
      .mockResolvedValue(
        new Response(null, { status: 307, headers: { location: "https://elsewhere.invalid" } }),
      );
    vi.stubGlobal("fetch", upstream);
    const response = await proxy(
      incoming("/jogadores/arenas", "GET", { cookie: "clicklance_player_session=player.fixture" }),
      context("jogadores/arenas"),
    );
    expect(response.status).toBe(502);
    expect(response.headers.get("location")).toBeNull();
    expect(upstream.mock.calls[0][1].redirect).toBe("manual");
  });
});

describe("explicit synthetic demonstration", () => {
  it("isolates saved replays by account and never grants admin through registration", async () => {
    enterDemoSession("player");
    const listed = await demoRequest<Page<Arena>>("/jogadores/arenas");
    expect(listed.items.length).toBeGreaterThan(0);
    const replays = await demoRequest<Page<Replay>>("/jogadores/quadras/quadra-1-1/lances");
    expect(replays.items.length).toBeGreaterThan(0);
    const event = replays.items[0].evento_id;
    await demoRequest(`/jogadores/biblioteca/${event}`, { method: "PUT" });
    expect(
      (await demoRequest<Page<Replay>>("/jogadores/biblioteca")).items.map((r) => r.evento_id),
    ).toContain(event);
    const email = `${crypto.randomUUID()}@example.invalid`,
      password = "synthetic-password";
    const user = await demoRequest<User>("/jogadores", {
      method: "POST",
      body: { email, password },
    });
    expect(user.platform_admin).toBeUndefined();
    await demoRequest("/jogadores/sessoes", { method: "POST", body: { email, password } });
    expect((await demoRequest<Page<Replay>>("/jogadores/biblioteca")).total).toBe(0);
    await expect(
      demoRequest("/admin/sessoes", { method: "POST", body: { email, password } }),
    ).rejects.toMatchObject({ status: 401 });
  });
  it("invalidates a closed account and does not simulate an actual email delivery", async () => {
    const email = `${crypto.randomUUID()}@example.invalid`,
      password = "synthetic-password";
    await demoRequest("/jogadores", { method: "POST", body: { email, password } });
    await demoRequest("/jogadores/sessoes", { method: "POST", body: { email, password } });
    await demoRequest("/jogadores/conta", { method: "DELETE" });
    await expect(demoRequest("/jogadores/sessoes/atual")).rejects.toBeInstanceOf(ApiError);
    await expect(
      demoRequest("/jogadores/sessoes", { method: "POST", body: { email, password } }),
    ).rejects.toMatchObject({ status: 401 });
    expect(
      (
        await demoRequest<{ message: string }>("/jogadores/recuperacao", {
          method: "POST",
          body: { email },
        })
      ).message,
    ).toContain("nenhum email");
  });
});

it("accepts signed HTTPS media while rejecting active or credential-bearing URLs", () => {
  expect(safeMediaUrl("https://private.example.invalid/video?signature=fixture")).toContain(
    "https://",
  );
  expect(safeMediaUrl("javascript:alert(1)")).toBeNull();
  expect(safeMediaUrl("data:video/mp4;base64,test")).toBeNull();
  expect(safeMediaUrl("https://user:password@private.example.invalid/video")).toBeNull();
});

it("keeps HTTP failures in HTTP mode without returning demonstration data", async () => {
  vi.stubEnv("NEXT_PUBLIC_API_MODE", "http");
  vi.resetModules();
  const upstream = vi.fn().mockRejectedValue(new TypeError("fixture unavailable"));
  vi.stubGlobal("fetch", upstream);
  const real = await import("./api");
  expect(real.isDemo).toBe(false);
  await expect(real.platform.arenas()).rejects.toMatchObject({ code: "NETWORK_ERROR" });
  expect(upstream).toHaveBeenCalledWith(
    "/api/backend/jogadores/arenas?page=0&size=20",
    expect.objectContaining({ credentials: "same-origin", cache: "no-store" }),
  );
});
