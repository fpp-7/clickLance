"use client";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { LogOut, Bookmark, ShieldCheck } from "lucide-react";
import { useSession } from "@/lib/platform/session";
import { platform, isDemo } from "@/lib/platform/api";

export function AccountPage() {
  const session = useSession();
  const router = useRouter();
  const [confirm, setConfirm] = useState(false);
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  if (session.loading)
    return (
      <div className="page-shell" role="status">
        Conferindo sua conta…
      </div>
    );
  if (!session.user)
    return (
      <div className="page-shell">
        <h1 className="page-heading">Sua conta</h1>
        <p style={{ margin: "24px 0" }} className="muted">
          Entre para consultar sua conta e sua biblioteca.
        </p>
        <Link href="/entrar?next=/conta" className="button">
          Entrar na minha conta
        </Link>
      </div>
    );
  async function logout() {
    setBusy(true);
    setError("");
    try {
      await session.logout();
      router.replace("/");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Não foi possível encerrar a sessão.");
    } finally {
      setBusy(false);
    }
  }
  async function close(event: FormEvent) {
    event.preventDefault();
    if (email.toLowerCase().trim() !== session.user?.email.toLowerCase()) return;
    setBusy(true);
    setError("");
    try {
      await platform.closeAccount();
      await session.refresh();
      router.replace("/entrar?encerrada=1");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Não foi possível encerrar a conta.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="page-shell">
      <h1 className="page-heading">Sua conta, seu jogo.</h1>
      <div className="account-layout">
        <div>
          <p className="muted" style={{ fontSize: 14, lineHeight: 1.8 }}>
            Gerencie seu acesso e os momentos que você escolheu guardar.
          </p>
          <p style={{ fontWeight: 650, marginTop: 25, overflowWrap: "anywhere" }}>
            {session.user.email}
          </p>
          {isDemo && (
            <p className="eyebrow" style={{ marginTop: 8 }}>
              Conta de demonstração
            </p>
          )}
        </div>
        <div className="account-sections">
          {error && (
            <div className="notice error" role="alert">
              {error}
            </div>
          )}
          <section className="panel">
            <Bookmark size={23} color="var(--color-primary)" style={{ marginBottom: 16 }} />
            <h2>Seus melhores momentos</h2>
            <p>
              Sua biblioteca guarda até 100 lances enquanto a conta estiver ativa. Remover um lance
              da biblioteca não afeta o que outros jogadores salvaram.
            </p>
            <Link href="/biblioteca" className="button secondary">
              Abrir minha biblioteca
            </Link>
          </section>
          <section className="panel">
            <ShieldCheck size={23} color="var(--color-primary)" style={{ marginBottom: 16 }} />
            <h2>Acesso à conta</h2>
            <p>
              Sua sessão dura até uma hora. Para trocar a senha, solicite um código de recuperação
              por email. A troca encerra as sessões anteriores.
            </p>
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
              <Link href="/recuperar-acesso" className="button secondary">
                Alterar senha
              </Link>
              <button type="button" onClick={logout} disabled={busy} className="button ghost">
                <LogOut size={17} />
                Sair da conta
              </button>
            </div>
          </section>
          <section className="panel">
            <h2>Encerrar conta</h2>
            <p>
              O encerramento remove seus vínculos com os lances e encerra seu acesso. Vídeos salvos
              por outros jogadores são preservados. Registros operacionais podem permanecer conforme
              a política do piloto.
            </p>
            {!confirm ? (
              <button
                type="button"
                className="text-link"
                style={{ color: "var(--color-danger)", fontSize: 13 }}
                onClick={() => setConfirm(true)}
              >
                Quero encerrar minha conta
              </button>
            ) : (
              <form onSubmit={close} className="auth-form">
                <div className="notice error">
                  Esta ação não pode ser desfeita. Sua biblioteca será removida.
                </div>
                <label className="field">
                  Confirme seu email
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    autoComplete="email"
                  />
                </label>
                <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                  <button
                    className="button danger"
                    disabled={
                      busy || email.toLowerCase().trim() !== session.user.email.toLowerCase()
                    }
                  >
                    Encerrar minha conta
                  </button>
                  <button
                    type="button"
                    className="button secondary"
                    onClick={() => setConfirm(false)}
                    disabled={busy}
                  >
                    Cancelar
                  </button>
                </div>
              </form>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
