"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Building2, ArrowUpRight, LogOut } from "lucide-react";
import { LinkMarca } from "@/components/ui/Marca";
import { useSession } from "@/lib/platform/session";
import { isDemo } from "@/lib/platform/api";
import { ErrorNotice } from "./shared";
import s from "./admin.module.css";
import { useState } from "react";

export function AdminShell({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  const router = useRouter();
  const session = useSession("admin");
  const [error, setError] = useState<unknown>();
  if (path === "/admin/entrar") return children;
  if (session.loading)
    return (
      <main className={s.login}>
        <p role="status">Conferindo sua sessão…</p>
      </main>
    );
  if (!session.user)
    return (
      <main className={`${s.login} ${s.workspace}`}>
        <div className={s.loginInner}>
          <LinkMarca />
          <h1>Acesso à operação</h1>
          <p className={s.muted}>Entre com a conta administrativa da sua arena para continuar.</p>
          <Link className="button" href={`/admin/entrar?next=${encodeURIComponent(path)}`}>
            Entrar na administração
          </Link>
          <ErrorNotice error={session.error} />
        </div>
      </main>
    );
  async function logout() {
    try {
      await session.logout();
      router.push("/admin/entrar");
    } catch (e) {
      setError(e);
    }
  }
  return (
    <div className={s.shell}>
      <aside className={s.rail}>
        <div>
          <LinkMarca />
          <p className={s.railTitle}>Administração</p>
        </div>
        <nav className={s.railNav} aria-label="Administração">
          <Link href="/admin" aria-current="page">
            <Building2 size={18} />
            Empresas e quadras
          </Link>
          <Link href="/arenas">
            Ver plataforma
            <ArrowUpRight size={16} />
          </Link>
        </nav>
        <div className={s.identity}>
          <div>
            <span className={s.status}>
              {session.user.platform_admin ? "Plataforma" : "Empresa"}
            </span>
            <p>{session.user.email}</p>
          </div>
          <button className="button ghost" onClick={logout}>
            <LogOut size={16} />
            Sair
          </button>
        </div>
      </aside>
      <main className={s.content}>
        {isDemo && (
          <p className={s.notice} style={{ marginBottom: 24 }}>
            Demonstração: as alterações nesta área são fictícias e ficam apenas nesta sessão.
          </p>
        )}
        <ErrorNotice error={error} />
        {children}
      </main>
    </div>
  );
}
