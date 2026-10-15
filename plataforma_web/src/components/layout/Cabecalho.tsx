import Link from "next/link";
import { Search } from "lucide-react";
import { LinkMarca } from "@/components/ui/Marca";

export function Cabecalho() {
  return (
    <header className="border-border/60 bg-background/85 sticky top-0 z-20 border-b backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-6xl items-center justify-between px-4">
        <LinkMarca tamanho="sm" />
        <nav aria-label="Principal">
          <Link
            href="/arenas"
            className="text-muted hover:bg-surface-2 hover:text-foreground flex h-10 items-center gap-2 rounded-full px-3 text-sm font-medium"
          >
            <Search className="size-4" aria-hidden="true" />
            Buscar quadra
          </Link>
        </nav>
      </div>
    </header>
  );
}
