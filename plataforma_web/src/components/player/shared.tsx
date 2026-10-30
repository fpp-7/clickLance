"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  LoaderCircle,
  LockKeyhole,
  RefreshCw,
  SearchX,
} from "lucide-react";
import { useState, type ReactNode } from "react";
import { isDemo } from "@/lib/platform/api";
import { useSession } from "@/lib/platform/session";
import styles from "./Player.module.css";

export function PlayerGate({ children }: { children: ReactNode }) {
  const { user, loading, enterDemo, error, refresh } = useSession("player");
  const pathname = usePathname();
  const [pending, setPending] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  if (loading) return <Loading />;
  if (user) return children;
  return (
    <div className={styles.page}>
      <div className={styles.empty}>
        <LockKeyhole aria-hidden="true" />
        <h1 className={styles.title}>Seu jogo está por aqui.</h1>
        <p>
          Entre para encontrar sua quadra, assistir aos lances e guardar os favoritos na sua
          biblioteca.
        </p>
        {(failure || error) && <p role="alert">{failure || error}</p>}
        <div className={styles.emptyActions}>
          <Link className="button" href={`/entrar?next=${encodeURIComponent(pathname)}`}>
            Entrar na minha conta
          </Link>
          {isDemo && (
            <button
              className="button secondary"
              disabled={pending}
              onClick={async () => {
                setPending(true);
                setFailure(null);
                try {
                  await enterDemo();
                } catch {
                  setFailure("Não foi possível abrir a demonstração. Tente novamente.");
                } finally {
                  setPending(false);
                }
              }}
            >
              {pending ? "Abrindo…" : "Explorar demonstração"}
            </button>
          )}
          {!isDemo && (
            <Link
              className="button secondary"
              href={`/cadastro?next=${encodeURIComponent(pathname)}`}
            >
              Criar conta
            </Link>
          )}
          {error && (
            <button className="button secondary" onClick={() => void refresh()}>
              Conferir conexão
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export function Loading({ label = "Buscando seus lances…" }: { label?: string }) {
  return (
    <div className={styles.loading} role="status">
      <LoaderCircle className={styles.spinner} size={20} aria-hidden="true" />
      {label}
    </div>
  );
}

export function Failure({ error, retry }: { error: unknown; retry: () => void }) {
  const problem = error as { status?: number; message?: string; correlationId?: string };
  return (
    <div className={styles.empty} role="alert">
      <RefreshCw aria-hidden="true" />
      <h2>
        {problem.status === 404
          ? "Este lance não está disponível"
          : problem.status === 401
            ? "Sua sessão terminou"
            : "Não conseguimos carregar agora"}
      </h2>
      <p>
        {problem.status === 404
          ? "O conteúdo pode ter expirado ou sido retirado. Volte para a quadra ou procure na sua biblioteca."
          : problem.status === 401
            ? "Entre novamente para continuar de onde parou."
            : errorMessage(error)}
      </p>
      {problem.status === 401 ? (
        <Link className="button" href="/entrar">
          Entrar novamente
        </Link>
      ) : (
        <button className="button secondary" onClick={retry}>
          Tentar novamente
        </button>
      )}
      {problem.correlationId && (
        <p className={styles.sideNote}>Código para suporte: {problem.correlationId}</p>
      )}
    </div>
  );
}

export function Empty({
  title,
  children,
  action,
}: {
  title: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className={styles.empty}>
      <SearchX aria-hidden="true" />
      <h2>{title}</h2>
      <p>{children}</p>
      {action}
    </div>
  );
}

export function Back({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link href={href} className={styles.back}>
      <ArrowLeft size={15} aria-hidden="true" />
      {children}
    </Link>
  );
}

export function Pagination({
  page,
  size,
  total,
  change,
}: {
  page: number;
  size: number;
  total: number;
  change: (page: number) => void;
}) {
  if (total <= size) return null;
  return (
    <nav className={styles.pagination} aria-label="Paginação">
      <button disabled={!page} onClick={() => change(page - 1)} aria-label="Página anterior">
        <ChevronLeft size={17} />
      </button>
      <span>
        Página {page + 1} de {Math.ceil(total / size)}
      </span>
      <button
        disabled={(page + 1) * size >= total}
        onClick={() => change(page + 1)}
        aria-label="Próxima página"
      >
        <ChevronRight size={17} />
      </button>
    </nav>
  );
}

export function PitchArtwork({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 600 350" fill="none" className={className} aria-hidden="true">
      <path d="M48 32H552V318H48Z" stroke="currentColor" strokeWidth="2" />
      <path
        d="M300 32V318M48 87H126V263H48M552 87H474V263H552M48 128H80V222H48M552 128H520V222H552"
        stroke="currentColor"
        strokeWidth="2"
      />
      <circle cx="300" cy="175" r="56" stroke="currentColor" strokeWidth="2" />
      <circle cx="300" cy="175" r="3" fill="currentColor" />
      <path
        d="M126 141C150 152 150 198 126 209M474 141C450 152 450 198 474 209"
        stroke="currentColor"
        strokeWidth="2"
      />
    </svg>
  );
}

export function localDateTime(
  value: string,
  zone = "America/Sao_Paulo",
  options?: Intl.DateTimeFormatOptions,
) {
  try {
    return new Intl.DateTimeFormat("pt-BR", { timeZone: zone, ...options }).format(new Date(value));
  } catch {
    return new Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo", ...options }).format(
      new Date(value),
    );
  }
}

export function errorMessage(error: unknown) {
  const problem = error as { code?: string; status?: number; message?: string };
  if (problem.code === "LIBRARY_LIMIT_REACHED")
    return "Sua biblioteca chegou a 100 lances. Remova um deles para guardar outro.";
  if (problem.status === 401) return "Sua sessão terminou. Entre novamente para continuar.";
  if (problem.status === 404)
    return "Este lance não está disponível. Ele pode ter expirado ou sido retirado.";
  if (problem.status === 429)
    return "Muitas solicitações em pouco tempo. Aguarde um minuto e tente novamente.";
  return problem.message || "A conexão falhou. Confira sua internet e tente novamente.";
}
