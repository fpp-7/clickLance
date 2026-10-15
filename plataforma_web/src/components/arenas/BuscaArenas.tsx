"use client";

import { SearchX } from "lucide-react";
import { useState } from "react";
import { useValorAtrasado } from "@/hooks/useValorAtrasado";
import { useArenas } from "@/lib/consultas/arenas";
import { CampoBusca } from "@/components/ui/CampoBusca";
import { EstadoErro } from "@/components/ui/EstadoErro";
import { EstadoVazio } from "@/components/ui/EstadoVazio";
import { CartaoArena, CartaoArenaEsqueleto } from "./CartaoArena";

export function BuscaArenas() {
  const [termo, setTermo] = useState("");
  const termoAtrasado = useValorAtrasado(termo);
  const arenas = useArenas(termoAtrasado);

  return (
    <div className="space-y-4">
      <CampoBusca
        valor={termo}
        aoMudar={setTermo}
        rotulo="Buscar arena ou quadra"
        placeholder="Nome da arena, bairro ou cidade"
        autoFocus
      />

      {arenas.isPending && (
        <ul className="space-y-3" aria-label="Carregando arenas">
          {Array.from({ length: 4 }, (_, i) => (
            <li key={i}>
              <CartaoArenaEsqueleto />
            </li>
          ))}
        </ul>
      )}

      {arenas.isError && (
        <EstadoErro
          erro={arenas.error}
          aoTentarDeNovo={() => arenas.refetch()}
          tentando={arenas.isRefetching}
        />
      )}

      {arenas.isSuccess && arenas.data.length === 0 && (
        <EstadoVazio
          icone={SearchX}
          titulo="Nenhuma arena com esse nome"
          descricao="Tente buscar pelo bairro ou pela cidade. Se a sua quadra ainda não usa o Click Lance, conta pra gente."
        />
      )}

      {arenas.isSuccess && arenas.data.length > 0 && (
        <ul className="space-y-3" aria-busy={arenas.isFetching || undefined}>
          {arenas.data.map((arena) => (
            <li key={arena.id}>
              <CartaoArena arena={arena} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
