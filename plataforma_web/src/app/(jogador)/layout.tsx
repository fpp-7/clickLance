import { Cabecalho } from "@/components/layout/Cabecalho";
import { Rodape } from "@/components/layout/Rodape";

/** Fluxo do jogador: busca de quadra, lista de lances, player. Pensado para o celular na quadra. */
export default function LayoutJogador({ children }: LayoutProps<"/">) {
  return (
    <>
      <Cabecalho />
      <main id="conteudo" className="pb-safe flex-1">
        {children}
      </main>
      <Rodape />
    </>
  );
}
