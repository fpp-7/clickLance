"use client";

import { MapPin } from "lucide-react";
import { useArenaPorSlug } from "@/lib/consultas/arenas";
import { useQuadrasDaArena } from "@/lib/consultas/quadras";
import { Esqueleto } from "@/components/ui/Esqueleto";
import { EstadoErro } from "@/components/ui/EstadoErro";
import { CartaoQuadra } from "./CartaoQuadra";

export function DetalheArena({ slug }: { slug: string }) {
  const arena = useArenaPorSlug(slug);
  const quadras = useQuadrasDaArena(arena.data?.id);

  if (arena.isPending) {
    return (
      <div className="space-y-6" aria-label="Carregando arena">
        <div className="space-y-2">
          <Esqueleto className="h-8 w-2/3" />
          <Esqueleto className="h-4 w-1/2" />
        </div>
        <div className="space-y-3">
          <Esqueleto className="h-18" />
          <Esqueleto className="h-18" />
        </div>
      </div>
    );
  }

  if (arena.isError) {
    return <EstadoErro erro={arena.error} aoTentarDeNovo={() => arena.refetch()} />;
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold tracking-wide uppercase">
          {arena.data.nome}
        </h1>
        <p className="text-muted mt-1 flex items-center gap-1 text-sm">
          <MapPin className="size-4" aria-hidden="true" />
          {arena.data.bairro}, {arena.data.cidade} · {arena.data.uf}
        </p>
      </header>

      <section aria-labelledby="titulo-quadras">
        <h2
          id="titulo-quadras"
          className="text-muted mb-3 text-sm font-semibold tracking-wide uppercase"
        >
          Quadras
        </h2>

        {quadras.isPending && (
          <div className="space-y-3">
            <Esqueleto className="h-18" />
            <Esqueleto className="h-18" />
          </div>
        )}

        {quadras.isError && (
          <EstadoErro erro={quadras.error} aoTentarDeNovo={() => quadras.refetch()} />
        )}

        {quadras.isSuccess && (
          <ul className="space-y-3">
            {quadras.data.map((quadra) => (
              <li key={quadra.id}>
                <CartaoQuadra quadra={quadra} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
