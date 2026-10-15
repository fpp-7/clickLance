import { Cabecalho } from "@/components/layout/Cabecalho";

/** Fluxo do jogador: busca de quadra, lista de lances, player. Pensado para o celular na quadra. */
export default function LayoutJogador({ children }: LayoutProps<"/">) {
  return (
    <>
      <Cabecalho />
      <main className="pb-safe mx-auto w-full max-w-lg flex-1 px-4 py-6">{children}</main>
    </>
  );
}
