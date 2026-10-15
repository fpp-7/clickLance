import Link from "next/link";
import { ChevronRight, MapPin } from "lucide-react";
import type { Arena } from "@/lib/api";

function iniciais(nome: string): string {
  return nome
    .split(/\s+/)
    .filter((palavra) => palavra.length > 2)
    .slice(0, 2)
    .map((palavra) => palavra[0])
    .join("")
    .toUpperCase();
}

export function CartaoArena({ arena }: { arena: Arena }) {
  return (
    <Link
      href={`/arenas/${arena.slug}`}
      className="rounded-card border-border bg-surface hover:border-muted/60 active:bg-surface-2 flex items-center gap-4 border p-4 transition"
    >
      <span
        aria-hidden="true"
        className="font-display text-on-primary flex size-12 shrink-0 items-center justify-center rounded-xl text-lg font-bold"
        style={{ backgroundColor: arena.corPrimaria ?? "var(--color-primary)" }}
      >
        {iniciais(arena.nome)}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate font-semibold">{arena.nome}</span>
        <span className="text-muted mt-0.5 flex items-center gap-1 text-sm">
          <MapPin className="size-3.5 shrink-0" aria-hidden="true" />
          <span className="truncate">
            {arena.bairro}, {arena.cidade} · {arena.uf}
          </span>
        </span>
      </span>
      <span className="text-muted flex shrink-0 items-center gap-1 text-sm">
        {arena.totalQuadras} {arena.totalQuadras === 1 ? "quadra" : "quadras"}
        <ChevronRight className="size-5" aria-hidden="true" />
      </span>
    </Link>
  );
}

export function CartaoArenaEsqueleto() {
  return (
    <div className="rounded-card border-border bg-surface flex items-center gap-4 border p-4">
      <div className="bg-surface-2 size-12 shrink-0 animate-pulse rounded-xl" />
      <div className="flex-1 space-y-2">
        <div className="bg-surface-2 h-4 w-2/3 animate-pulse rounded" />
        <div className="bg-surface-2 h-3 w-1/2 animate-pulse rounded" />
      </div>
    </div>
  );
}
