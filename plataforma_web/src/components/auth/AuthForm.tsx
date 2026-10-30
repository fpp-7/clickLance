"use client";
import Image from "next/image";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Eye, EyeOff, FlaskConical, LoaderCircle, ArrowLeft, CircleCheck } from "lucide-react";
import { platform, isDemo, ApiError } from "@/lib/platform/api";
import { useSession } from "@/lib/platform/session";
import { safePlayerReturn } from "@/lib/platform/navigation";

type Mode = "login" | "register" | "recover" | "reset";
const copy = {
  login: {
    title: "Bom ter você de volta.",
    text: "O jogo não acaba no apito. Seus melhores momentos estão esperando por você.",
    form: "Entre no jogo",
    note: "Acesse sua conta para encontrar e guardar seus lances.",
    action: "Entrar na minha conta",
  },
  register: {
    title: "Sua história em campo.",
    text: "Tem lance que merece sair do grupo e ficar na memória. Crie sua conta e guarde os seus.",
    form: "Crie sua conta",
    note: "Comece gratuitamente. Sua biblioteca pode guardar até 100 lances.",
    action: "Criar minha conta",
  },
  recover: {
    title: "O caminho de volta.",
    text: "Recupere o acesso e continue de onde parou. Seus lances ficam na sua biblioteca.",
    form: "Recuperar acesso",
    note: "Informe o email da sua conta para receber as instruções de recuperação.",
    action: "Enviar instruções",
  },
  reset: {
    title: "Novo acesso. Mesmo jogo.",
    text: "Defina uma nova senha para voltar à sua conta. As sessões anteriores serão encerradas.",
    form: "Defina sua nova senha",
    note: "Cole o código de recuperação recebido por email. Ele pode ser usado uma vez, em até 15 minutos.",
    action: "Salvar nova senha",
  },
};
export function AuthForm({ mode }: { mode: Mode }) {
  const content = copy[mode];
  const router = useRouter();
  const params = useSearchParams();
  const session = useSession("player");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [repeat, setRepeat] = useState("");
  const [token, setToken] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [correlation, setCorrelation] = useState("");
  const [done, setDone] = useState(false);
  const [created, setCreated] = useState(false);
  const next = safePlayerReturn(params.get("next"));
  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setCorrelation("");
    if (mode === "register" || mode === "reset") {
      const bytes = new TextEncoder().encode(password).length;
      if (bytes < 12 || bytes > 72) {
        setError("Use uma senha de 12 a 72 bytes. Acentos e emojis podem ocupar mais de um byte.");
        return;
      }
      if (repeat !== password) {
        setError("As senhas precisam ser iguais.");
        return;
      }
    }
    setBusy(true);
    try {
      if (mode === "login") {
        await session.login(email, password);
        router.replace(next);
      }
      if (mode === "register") {
        if (!created) {
          await platform.register(email, password);
          setCreated(true);
        }
        await session.login(email, password);
        router.replace(next);
      }
      if (mode === "recover") {
        await platform.recover(email);
        setDone(true);
      }
      if (mode === "reset") {
        await platform.reset(token, password);
        setPassword("");
        setRepeat("");
        setToken("");
        setDone(true);
        await session.refresh();
      }
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : "Não foi possível continuar. Tente novamente.",
      );
      if (failure instanceof ApiError && failure.correlationId)
        setCorrelation(failure.correlationId);
    } finally {
      setBusy(false);
    }
  }
  async function demo() {
    setBusy(true);
    setError("");
    try {
      await session.enterDemo();
      router.replace(next);
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : "Não foi possível abrir a demonstração.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-layout">
      <aside className="auth-aside">
        <h1>{content.title}</h1>
        <p>{content.text}</p>
        <div className="auth-photo">
          <Image
            src="/images/society-hero-v1.png"
            alt="Imagem ilustrativa de um jogo de society"
            fill
            sizes="440px"
          />
        </div>
      </aside>
      <section className="auth-card" aria-labelledby="form-title">
        <h2 id="form-title">{content.form}</h2>
        <p className="auth-description">{content.note}</p>
        {mode === "login" && params.get("encerrada") === "1" && (
          <div className="notice success" role="status" style={{ marginBottom: 20 }}>
            Sua conta foi encerrada e sua biblioteca foi removida.
          </div>
        )}
        {done ? (
          <div className="auth-form">
            <div className="notice success" role="status">
              <CircleCheck size={20} style={{ marginBottom: 8 }} />
              {mode === "recover"
                ? "Se a conta estiver elegível, você receberá as instruções no email informado. Confira também a pasta de spam."
                : "Sua senha foi alterada. Entre novamente para continuar."}
            </div>
            {mode === "recover" && (
              <Link href="/redefinir-senha" className="button">
                Já recebi o código
              </Link>
            )}
            <Link className="text-link" href="/entrar">
              Voltar para entrar
            </Link>
          </div>
        ) : (
          <>
            <form onSubmit={submit} className="auth-form">
              {created && error && (
                <div className="notice success" role="status">
                  Sua conta foi criada.{" "}
                  <Link href="/entrar" className="text-link">
                    Entre na sua conta para continuar.
                  </Link>
                </div>
              )}
              {mode !== "reset" && (
                <label className="field">
                  Email
                  <input
                    type="email"
                    name="email"
                    required
                    autoComplete="email"
                    placeholder="voce@email.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    maxLength={254}
                  />
                </label>
              )}
              {mode === "reset" && (
                <label className="field">
                  Código de recuperação
                  <input
                    type="text"
                    name="token"
                    required
                    autoComplete="off"
                    spellCheck={false}
                    value={token}
                    onChange={(e) => setToken(e.target.value.trim())}
                    maxLength={43}
                  />
                </label>
              )}
              {mode !== "recover" && (
                <div className="field">
                  <label htmlFor="auth-password">{mode === "reset" ? "Nova senha" : "Senha"}</label>
                  <div style={{ position: "relative" }}>
                    <input
                      style={{ paddingRight: 47 }}
                      type={show ? "text" : "password"}
                      name="password"
                      id="auth-password"
                      aria-describedby={mode !== "login" ? "password-help" : undefined}
                      autoComplete={mode === "login" ? "current-password" : "new-password"}
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                    />
                    <button
                      type="button"
                      onClick={() => setShow(!show)}
                      aria-label={show ? "Ocultar senha" : "Mostrar senha"}
                      style={{
                        position: "absolute",
                        right: 0,
                        top: 0,
                        width: 46,
                        height: 47,
                        display: "grid",
                        placeItems: "center",
                      }}
                    >
                      {show ? <EyeOff size={17} /> : <Eye size={17} />}
                    </button>
                  </div>
                  {mode !== "login" && (
                    <small id="password-help">
                      Use pelo menos 12 caracteres. Evite senhas já usadas em outros sites.
                    </small>
                  )}
                </div>
              )}
              {(mode === "register" || mode === "reset") && (
                <label className="field">
                  Confirme a senha
                  <input
                    type={show ? "text" : "password"}
                    name="repeat"
                    required
                    autoComplete="new-password"
                    value={repeat}
                    onChange={(e) => setRepeat(e.target.value)}
                  />
                </label>
              )}
              {mode === "login" && (
                <div className="auth-links">
                  <span className="muted">Bom jogo começa por aqui.</span>
                  <Link href="/recuperar-acesso" className="text-link">
                    Esqueci minha senha
                  </Link>
                </div>
              )}
              {error && (
                <div className="notice error" role="alert">
                  {error}
                  {correlation && (
                    <small style={{ display: "block", marginTop: 7 }}>
                      Código de suporte: {correlation}
                    </small>
                  )}
                </div>
              )}
              <button className="button" type="submit" disabled={busy}>
                {busy && <LoaderCircle size={16} className="animate-spin" />}
                {busy ? "Só um instante…" : created ? copy.login.action : content.action}
              </button>
            </form>
            {(mode === "login" || mode === "register") && (
              <>
                <div className="auth-divider">ou</div>
                {isDemo && (
                  <button
                    type="button"
                    onClick={demo}
                    disabled={busy}
                    className="button secondary"
                    style={{ width: "100%" }}
                  >
                    <FlaskConical size={16} />
                    Explorar demonstração
                  </button>
                )}
                <p
                  style={{
                    textAlign: "center",
                    fontSize: 12,
                    marginTop: 23,
                    color: "var(--color-muted)",
                  }}
                >
                  {mode === "login" ? "Primeira vez por aqui? " : "Já tem uma conta? "}
                  <Link
                    href={`${mode === "login" ? "/cadastro" : "/entrar"}?next=${encodeURIComponent(next)}`}
                    className="text-link"
                  >
                    {mode === "login" ? "Criar conta" : "Entrar"}
                  </Link>
                </p>
              </>
            )}
            {(mode === "recover" || mode === "reset") && (
              <Link
                href="/entrar"
                className="text-link"
                style={{ display: "inline-flex", gap: 7, marginTop: 24, fontSize: 12 }}
              >
                <ArrowLeft size={15} />
                Voltar para entrar
              </Link>
            )}
          </>
        )}
      </section>
    </div>
  );
}
