"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { Menu, X, UserRound, FlaskConical } from "lucide-react";
import { LinkMarca } from "@/components/ui/Marca";
import { isDemo } from "@/lib/platform/api";
import { useSession } from "@/lib/platform/session";

export function Cabecalho() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const { user } = useSession("player");
  return (
    <>
      <a href="#conteudo" className="skip-link">
        Pular para o conteúdo
      </a>
      {isDemo && (
        <div className="demo-bar">
          <FlaskConical size={12} aria-hidden="true" />
          Demonstração interativa. Arenas e lances são exemplos.
        </div>
      )}
      <header className="site-header">
        <div className="header-inner">
          <LinkMarca tamanho="md" />
          <nav className={`header-links ${open ? "open" : ""}`} aria-label="Navegação principal">
            {[
              ["/arenas", "Encontrar quadra"],
              ["/biblioteca", "Minha biblioteca"],
              ["/#como-funciona", "Como funciona"],
            ].map(([href, label]) => (
              <Link
                key={href}
                href={href}
                onClick={() => setOpen(false)}
                aria-current={pathname.startsWith(href) ? "page" : undefined}
              >
                {label}
              </Link>
            ))}
          </nav>
          <div className="header-account">
            {user ? (
              <Link href="/conta" className="button secondary">
                <UserRound size={16} aria-hidden="true" />
                Minha conta
              </Link>
            ) : (
              <>
                <Link href="/entrar" className="login-link">
                  Entrar
                </Link>
                <Link href="/cadastro" className="button">
                  Criar conta
                </Link>
              </>
            )}
            <button
              className="mobile-menu-button"
              type="button"
              aria-label={open ? "Fechar menu" : "Abrir menu"}
              aria-expanded={open}
              onClick={() => setOpen(!open)}
            >
              {open ? <X size={22} /> : <Menu size={22} />}
            </button>
          </div>
        </div>
      </header>
    </>
  );
}
