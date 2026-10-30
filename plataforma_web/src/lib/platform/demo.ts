import { ApiError, errorMessage } from "./errors";
import type { Arena, Court, Page, Replay, RequestOptions, Role, User } from "./types";

interface Account {
  user: User;
  password: string;
  closed?: boolean;
}
interface DemoCompany extends Arena {
  active: boolean;
  retencao_dias: number;
}
interface DemoCourt extends Court {
  empresa_id: string;
  active: boolean;
  perfil_captura: Record<string, unknown>;
}
interface DemoDevice {
  dispositivo_id: string;
  quadra_id: string;
  empresa_id: string;
  active: boolean;
  bucket: string;
}
const accounts = new Map<string, Account>();
const sessions: Partial<Record<Role, { user: User; expires: number }>> = {};
const libraries = new Map<string, Set<string>>();
const blocked = new Set<string>();
const closed = new Set<string>();
const configurations = new Map<string, { version: string; settings: Record<string, unknown> }>();
const names = [
  "Arena Bola na Rede",
  "Society Vila Nova",
  "Arena Gol de Placa",
  "Estação Futebol",
  "Arena Litoral",
  "Ginásio Central",
];
const companies: DemoCompany[] = names.map((nome, i) => ({
  empresa_id: `arena-${i + 1}`,
  nome,
  active: true,
  retencao_dias: 2,
}));
const courts: DemoCourt[] = companies.flatMap((company, i) =>
  [0, 1].map((j) => ({
    quadra_id: `quadra-${i + 1}-${j + 1}`,
    empresa_id: company.empresa_id,
    nome: j === 0 ? "Quadra principal" : "Quadra 2",
    timezone: "America/Sao_Paulo",
    active: true,
    perfil_captura: {
      REPLAY_BEFORE_SECONDS: 120,
      REPLAY_AFTER_SECONDS: 0,
      SEGMENT_DURATION_SECONDS: 60,
      KEYFRAME_INTERVAL: 30,
    },
  })),
);
const devices: DemoDevice[] = courts.map((court, i) => ({
  dispositivo_id: `botao-${i + 1}`,
  quadra_id: court.quadra_id,
  empresa_id: court.empresa_id,
  active: true,
  bucket: "clicklance-demo",
}));
const moment = Date.now();
const replays: Replay[] = courts.flatMap((court, i) =>
  Array.from({ length: 12 }, (_, j) => {
    const at = moment - (j * 24 + 10) * 60000;
    return {
      evento_id: (i * 100 + j + 1).toString(16).padStart(32, "0"),
      empresa_id: court.empresa_id,
      quadra_id: court.quadra_id,
      timestamp: new Date(at).toISOString(),
      expira_em: new Date(at + 2 * 86400000).toISOString(),
      salvo: false,
    };
  }),
);

function fail(
  status: number,
  code = status === 404 ? "RESOURCE_NOT_FOUND" : "VALIDATION_FAILED",
): never {
  throw new ApiError(errorMessage(status, code), status, code);
}
function emailOf(value: unknown) {
  if (typeof value !== "string" || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())) fail(422);
  return value.trim().toLowerCase();
}
function passwordOf(value: unknown) {
  if (
    typeof value !== "string" ||
    new TextEncoder().encode(value).length < 12 ||
    new TextEncoder().encode(value).length > 72
  )
    fail(422);
  return value;
}
function bodyOf(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}
function page<T>(items: T[], url: URL): Page<T> {
  const current = Number(url.searchParams.get("page") ?? 0),
    size = Number(url.searchParams.get("size") ?? 20);
  if (
    !Number.isInteger(current) ||
    current < 0 ||
    !Number.isInteger(size) ||
    size < 1 ||
    size > 100
  )
    fail(422);
  return {
    items: items.slice(current * size, current * size + size),
    page: current,
    size,
    total: items.length,
  };
}
function current(role: Role): User {
  const session = sessions[role];
  if (!session || session.expires <= Date.now() || closed.has(session.user.usuario_id)) {
    delete sessions[role];
    fail(401, "UNAUTHENTICATED");
  }
  return session.user;
}
function savedBy(user: string) {
  if (!libraries.has(user)) libraries.set(user, new Set());
  return libraries.get(user)!;
}
function visible(replay: Replay, user: User) {
  const saved = savedBy(user.usuario_id).has(replay.evento_id);
  const origin = courts.find((q) => q.quadra_id === replay.quadra_id);
  const company = companies.find((c) => c.empresa_id === replay.empresa_id);
  const device = devices.find((d) => d.quadra_id === replay.quadra_id);
  return (
    !blocked.has(replay.evento_id) &&
    (saved ||
      (new Date(replay.expira_em).getTime() > Date.now() &&
        origin?.active &&
        company?.active &&
        device?.active))
  );
}
function summary(replay: Replay, user: User) {
  return { ...replay, salvo: savedBy(user.usuario_id).has(replay.evento_id) };
}
function dateOf(iso: string) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Sao_Paulo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(iso));
}
function hourOf(iso: string) {
  return Number(
    new Intl.DateTimeFormat("en-GB", {
      timeZone: "America/Sao_Paulo",
      hour: "2-digit",
      hourCycle: "h23",
    }).format(new Date(iso)),
  );
}

function seedAccount(role: Role) {
  const email = role === "admin" ? "operador@demo.clicklance.test" : "jogador@demo.clicklance.test";
  const password = role === "admin" ? "operador-demo123" : "jogador-demo123";
  if (!accounts.has(email) || accounts.get(email)?.closed)
    accounts.set(email, {
      password,
      user: {
        usuario_id: accounts.has(email)
          ? crypto.randomUUID()
          : role === "admin"
            ? "demo-admin"
            : "demo-player",
        email,
        ...(role === "admin"
          ? { platform_admin: true, empresa_ids: companies.map((c) => c.empresa_id) }
          : {}),
      },
    });
  return accounts.get(email)!;
}
export function enterDemoSession(role: Role) {
  const account = seedAccount(role);
  sessions[role] = { user: account.user, expires: Date.now() + 3600000 };
  return account.user;
}

/** Synthetic data lives only in this browser's memory. No real account, email or camera is involved. */
export async function demoRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  await new Promise((resolve) => setTimeout(resolve, 100));
  try {
    return handle(path, options) as T;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401)
      delete sessions[path.startsWith("/admin/") ? "admin" : "player"];
    throw error;
  }
}

function handle(path: string, options: RequestOptions): unknown {
  const url = new URL(path, "https://demo.clicklance.test"),
    p = url.pathname.split("/").filter(Boolean),
    method = options.method ?? "GET",
    body = bodyOf(options.body);
  const role: Role = p[0] === "admin" ? "admin" : "player";
  if (p[0] !== "admin" && p[0] !== "jogadores") fail(404);
  if (p.length === 1 && p[0] === "jogadores" && method === "POST") {
    const email = emailOf(body.email),
      password = passwordOf(body.password);
    if (accounts.has(email)) fail(409, "IDENTITY_CONFLICT");
    const user: User = { usuario_id: crypto.randomUUID(), email };
    accounts.set(email, { user, password });
    return user;
  }
  if (p[1] === "sessoes" && p.length === 2 && method === "POST") {
    seedAccount(role);
    const email = emailOf(body.email),
      password = passwordOf(body.password),
      account = accounts.get(email);
    if (
      !account ||
      account.password !== password ||
      account.closed ||
      (role === "admin") !== Boolean(account.user.platform_admin)
    )
      fail(401, "UNAUTHENTICATED");
    sessions[role] = { user: account.user, expires: Date.now() + 3600000 };
    return { authenticated: true };
  }
  if (p[0] === "jogadores" && p[1] === "recuperacao" && method === "POST") {
    emailOf(body.email);
    return {
      message:
        "Na demonstração, nenhum email é enviado. No ambiente real, você receberá as instruções se a conta estiver elegível.",
    };
  }
  if (p[0] === "jogadores" && p[1] === "redefinicao" && method === "POST") {
    passwordOf(body.password);
    fail(422, "INVALID_RESET_TOKEN");
  }
  const user = current(role);
  if (p[1] === "sessoes" && p[2] === "atual") {
    if (method === "DELETE") {
      delete sessions[role];
      return;
    }
    if (method === "GET") return user;
  }
  if (role === "admin") return admin(p, method, body, url, user);
  if (p[1] === "conta" && method === "DELETE") {
    closed.add(user.usuario_id);
    libraries.delete(user.usuario_id);
    const account = accounts.get(user.email);
    if (account) account.closed = true;
    delete sessions.player;
    return;
  }
  if (p[1] === "arenas") {
    if (p.length === 2 && method === "GET")
      return page(
        companies.filter((c) => c.active).map(({ empresa_id, nome }) => ({ empresa_id, nome })),
        url,
      );
    if (p.length === 4 && p[3] === "quadras" && method === "GET") {
      if (!companies.find((c) => c.empresa_id === p[2] && c.active)) fail(404);
      return page(
        courts
          .filter((c) => c.empresa_id === p[2] && c.active)
          .map(({ quadra_id, nome, timezone, empresa_id }) => ({
            quadra_id,
            nome,
            timezone,
            empresa_id,
          })),
        url,
      );
    }
  }
  if (p[1] === "quadras" && method === "GET") {
    const court = courts.find((c) => c.quadra_id === p[2] && c.active);
    if (!court || !companies.find((c) => c.empresa_id === court.empresa_id && c.active)) fail(404);
    const publicReplays = replays.filter(
      (r) =>
        r.quadra_id === court.quadra_id &&
        new Date(r.expira_em).getTime() > Date.now() &&
        visible(r, user),
    );
    const today = dateOf(new Date().toISOString()),
      date = url.searchParams.get("data") ?? today;
    const selected = publicReplays.filter((r) => dateOf(r.timestamp) === date);
    if (p[3] === "disponibilidade")
      return {
        quadra_id: court.quadra_id,
        timezone: court.timezone,
        hoje: today,
        data: date,
        datas: [...new Set(publicReplays.map((r) => dateOf(r.timestamp)))].sort().reverse(),
        slots: Array.from({ length: 24 }, (_, hour) => ({
          hora_inicio: hour,
          hora_fim: hour + 1,
          total: selected.filter((r) => hourOf(r.timestamp) === hour).length,
        })).filter((s) => s.total > 0),
        page: 0,
        size: 20,
      };
    if (p[3] === "lances") {
      const start = url.searchParams.get("hora_inicio"),
        end = url.searchParams.get("hora_fim");
      if ((start === null) !== (end === null)) fail(422);
      return page(
        selected
          .filter(
            (r) =>
              start === null ||
              (hourOf(r.timestamp) >= Number(start) && hourOf(r.timestamp) < Number(end)),
          )
          .sort((a, b) => a.timestamp.localeCompare(b.timestamp))
          .map((r) => summary(r, user)),
        url,
      );
    }
  }
  if (p[1] === "lances") {
    const replay = replays.find((r) => r.evento_id === p[2]);
    if (!replay || !visible(replay, user)) fail(404);
    if (p.length === 3 && method === "GET") return summary(replay, user);
    if (p[3] === "acesso" && method === "POST")
      return {
        url: "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
        expira_em: new Date(
          Math.min(
            Date.now() + 300000,
            savedBy(user.usuario_id).has(replay.evento_id)
              ? Infinity
              : new Date(replay.expira_em).getTime(),
          ),
        ).toISOString(),
      };
  }
  if (p[1] === "biblioteca") {
    const library = savedBy(user.usuario_id);
    if (p.length === 2 && method === "GET")
      return page(
        replays
          .filter((r) => library.has(r.evento_id) && visible(r, user))
          .map((r) => summary(r, user)),
        url,
      );
    if (method === "DELETE") {
      library.delete(p[2]);
      return;
    }
    if (method === "PUT") {
      const replay = replays.find((r) => r.evento_id === p[2]);
      if (!replay || !visible(replay, user)) fail(404);
      if (!library.has(replay.evento_id) && library.size >= 100) fail(409, "LIBRARY_LIMIT_REACHED");
      library.add(replay.evento_id);
      return summary(replay, user);
    }
  }
  fail(404);
}

function admin(
  p: string[],
  method: string,
  body: Record<string, unknown>,
  url: URL,
  user: User,
): unknown {
  if (!user.platform_admin) fail(403, "FORBIDDEN");
  if (p[1] === "empresas") {
    if (p.length === 2) {
      if (method === "GET") return page(companies, url);
      if (method === "POST") {
        const company = {
          empresa_id: crypto.randomUUID(),
          nome: String(body.nome ?? ""),
          active: Boolean(body.active),
          retencao_dias: Number(body.retencao_dias ?? 2),
        };
        companies.push(company);
        return company;
      }
    }
    const company = companies.find((c) => c.empresa_id === p[2]);
    if (!company) fail(404);
    if (p.length === 3 && method === "GET") return company;
    if (p[3] === "status" && method === "PATCH") {
      company.active = Boolean(body.active);
      return company;
    }
    if (p[3] === "auditoria") return page([], url);
    if (p[3] === "purga" || p[3] === "reconciliacao") fail(503, "PURGE_DISABLED");
    if (p[3] === "quadras") {
      if (p.length === 4) {
        if (method === "GET")
          return page(
            courts.filter((c) => c.empresa_id === company.empresa_id),
            url,
          );
        if (method === "POST") {
          const court = {
            quadra_id: crypto.randomUUID(),
            empresa_id: company.empresa_id,
            nome: String(body.nome),
            active: Boolean(body.active),
            timezone: String(body.timezone),
            perfil_captura: bodyOf(body.perfil_captura),
          };
          courts.push(court);
          return court;
        }
      }
      const court = courts.find((c) => c.quadra_id === p[4] && c.empresa_id === company.empresa_id);
      if (!court) fail(404);
      if (p.length === 5 && method === "GET") return court;
      if (p[5] === "status" && method === "PATCH") {
        court.active = Boolean(body.active);
        return court;
      }
      if (p[5] === "dispositivos") {
        if (method === "GET")
          return page(
            devices.filter((d) => d.quadra_id === court.quadra_id),
            url,
          );
        if (method === "POST") {
          const device = {
            dispositivo_id: String(body.dispositivo_id),
            quadra_id: court.quadra_id,
            empresa_id: court.empresa_id,
            active: Boolean(body.active),
            bucket: String(body.bucket),
          };
          if (devices.find((d) => d.dispositivo_id === device.dispositivo_id))
            fail(409, "IDENTITY_CONFLICT");
          devices.push(device);
          return {
            dispositivo: device,
            device_token: crypto.randomUUID().replaceAll("-", "") + "demoToken00",
          };
        }
      }
    }
  }
  if (p[1] === "dispositivos") {
    const device = devices.find((d) => d.dispositivo_id === p[2]);
    if (!device) fail(404);
    if (p.length === 3 && method === "GET") return device;
    if (p[3] === "status" && method === "PATCH") {
      device.active = Boolean(body.active);
      return device;
    }
    if (p[3] === "credenciais" && p[4] === "rotacao" && method === "POST")
      return {
        dispositivo: device,
        device_token: crypto.randomUUID().replaceAll("-", "") + "demoToken00",
      };
    if (p[3] === "saude" && method === "GET")
      return {
        dispositivo_id: device.dispositivo_id,
        contact_state: "CURRENT",
        health: {
          dispositivo_id: device.dispositivo_id,
          observed_at: new Date().toISOString(),
          received_at: new Date().toISOString(),
          gateway_version: "demo",
          capture_state: device.active ? "READY" : "UNAVAILABLE",
          pending_events: 0,
          oldest_pending_age_seconds: 0,
          capture_progress_age_seconds: 1,
        },
      };
    if (p[3] === "configuracoes" && method === "POST") {
      const previous = configurations.get(device.dispositivo_id);
      if ((body.base_version ?? null) !== (previous?.version ?? null))
        fail(409, "CONFIG_VERSION_CONFLICT");
      const config = {
        version: crypto.randomUUID(),
        settings: { ...previous?.settings, ...bodyOf(body.settings) },
      };
      configurations.set(device.dispositivo_id, config);
      return {
        ...config,
        dispositivo_id: device.dispositivo_id,
        published_by: user.usuario_id,
        published_at: new Date().toISOString(),
      };
    }
  }
  if (p[1] === "lances" && p[3] === "bloqueio" && method === "PATCH") {
    const replay = replays.find((r) => r.evento_id === p[2]);
    if (!replay) fail(404);
    if (body.blocked) blocked.add(replay.evento_id);
    else blocked.delete(replay.evento_id);
    return { evento_id: replay.evento_id, blocked: Boolean(body.blocked) };
  }
  if (p[1] === "metricas" && method === "GET") return {};
  fail(404);
}
