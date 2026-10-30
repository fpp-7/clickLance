"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError, platform } from "./api";
import type { Role, User } from "./types";

interface State {
  user: User | null;
  loading: boolean;
  error: string | null;
}
interface Sessions {
  states: Record<Role, State>;
  refresh: (role: Role) => Promise<void>;
  login: (role: Role, email: string, password: string) => Promise<User>;
  logout: (role: Role) => Promise<void>;
  enterDemo: (role: Role) => Promise<User>;
}
const Context = createContext<Sessions | null>(null);
const initial = (): State => ({ user: null, loading: true, error: null });

export function SessionProvider({ children }: { children: ReactNode }) {
  const cache = useQueryClient();
  const [states, setStates] = useState<Record<Role, State>>({
    player: initial(),
    admin: initial(),
  });
  const identities = useRef<Partial<Record<Role, string>>>({});
  const generations = useRef<Record<Role, number>>({ player: 0, admin: 0 });
  const update = useCallback(
    (role: Role, user: User | null, error: string | null = null) => {
      if (identities.current[role] !== user?.usuario_id) cache.clear();
      identities.current[role] = user?.usuario_id;
      setStates((previous) => ({ ...previous, [role]: { user, loading: false, error } }));
    },
    [cache],
  );
  const refresh = useCallback(
    async (role: Role) => {
      const generation = ++generations.current[role];
      try {
        const user = await platform.current(role);
        if (generation === generations.current[role]) update(role, user);
      } catch (error) {
        if (generation === generations.current[role])
          update(
            role,
            null,
            error instanceof ApiError && error.status === 401
              ? null
              : error instanceof Error
                ? error.message
                : "Não foi possível conferir a sessão.",
          );
      }
    },
    [update],
  );
  useEffect(() => {
    const versionCounters = generations.current;
    const handler = (event: Event) => {
      const { role, reason } = (event as CustomEvent<{ role: Role; reason: string }>).detail;
      if (reason === "expired") {
        generations.current[role]++;
        update(role, null);
      } else void refresh(role);
    };
    window.addEventListener("clicklance:session", handler);
    const initialCheck = window.setTimeout(() => {
      void refresh("player");
      void refresh("admin");
    }, 0);
    const timer = window.setInterval(() => {
      void refresh("player");
      void refresh("admin");
    }, 60000);
    const focus = () => {
      void refresh("player");
      void refresh("admin");
    };
    window.addEventListener("focus", focus);
    return () => {
      window.removeEventListener("clicklance:session", handler);
      window.removeEventListener("focus", focus);
      window.clearInterval(timer);
      window.clearTimeout(initialCheck);
      versionCounters.player++;
      versionCounters.admin++;
    };
  }, [refresh, update]);
  const login = async (role: Role, email: string, password: string) => {
    const user = await platform.login(email, password, role);
    generations.current[role]++;
    update(role, user);
    return user;
  };
  const logout = async (role: Role) => {
    await platform.logout(role);
    generations.current[role]++;
    update(role, null);
  };
  const enterDemo = async (role: Role) => {
    const user = await platform.enterDemo(role);
    generations.current[role]++;
    update(role, user);
    return user;
  };
  return (
    <Context.Provider value={{ states, refresh, login, logout, enterDemo }}>
      {children}
    </Context.Provider>
  );
}

export function useSession(role: Role = "player") {
  const context = useContext(Context);
  if (!context) throw new Error("SessionProvider precisa envolver a aplicação.");
  return {
    ...context.states[role],
    refresh: () => context.refresh(role),
    login: (email: string, password: string) => context.login(role, email, password),
    logout: () => context.logout(role),
    enterDemo: () => context.enterDemo(role),
  };
}
