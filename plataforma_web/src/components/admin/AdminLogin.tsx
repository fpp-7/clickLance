"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { LockKeyhole } from "lucide-react";
import { LinkMarca } from "@/components/ui/Marca";
import { useSession } from "@/lib/platform/session";
import { isDemo } from "@/lib/platform/api";
import { ErrorNotice } from "./shared";
import s from "./admin.module.css";

export function AdminLogin() {
  const router = useRouter();
  const session = useSession("admin");
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  function destination() {
    const next = new URLSearchParams(window.location.search).get("next");
    return next?.startsWith("/admin/") && !next.startsWith("//") ? next : "/admin";
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError(undefined);
    try {
      await session.login(String(data.get("email")), String(data.get("password")));
      router.push(destination());
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function demo() {
    setBusy(true);
    setError(undefined);
    try {
      await session.enterDemo();
      router.push("/admin");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className={`${s.login} ${s.workspace}`}>
      <div className={s.loginInner}>
        <LinkMarca />
        <div>
          <LockKeyhole size={24} color="#087d66" />
          <h1>Cuide do jogo.</h1>
          <p className={s.muted}>Acesse as quadras, os dispositivos e a operação da sua arena.</p>
        </div>
        <form className={`${s.section} ${s.loginForm}`} onSubmit={submit}>
          <label>
            Email administrativo
            <input
              name="email"
              type="email"
              autoComplete="username"
              required
              maxLength={254}
              placeholder="voce@sua-arena.com.br"
            />
          </label>
          <label>
            Senha
            <input
              name="password"
              type="password"
              autoComplete="current-password"
              required
              minLength={12}
              maxLength={72}
            />
          </label>
          <ErrorNotice error={error} />
          <button className="button" disabled={busy}>
            {busy ? "Entrando…" : "Entrar na administração"}
          </button>
          {isDemo && (
            <button type="button" className="button secondary" onClick={demo} disabled={busy}>
              Explorar administração demonstrativa
            </button>
          )}
        </form>
        <p className={s.small}>
          O acesso é fornecido pelo responsável da plataforma. O cadastro de jogador não concede
          permissão administrativa.
        </p>
        <Link href="/" className="text-link">
          Voltar para o ClickLance
        </Link>
      </div>
    </main>
  );
}
