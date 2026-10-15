import { Cabecalho } from "@/components/layout/Cabecalho";
import { BotaoLink } from "@/components/ui/Botao";

export default function PaginaNaoEncontrada() {
  return (
    <>
      <Cabecalho />
      <main className="mx-auto flex w-full max-w-lg flex-1 flex-col items-center justify-center px-4 py-16 text-center">
        <p className="font-display text-primary text-7xl font-bold">404</p>
        <h1 className="mt-2 text-xl font-semibold">Essa página não existe</h1>
        <p className="text-muted mt-2">
          O link pode estar errado ou a página saiu do ar. Procure sua quadra pelo nome.
        </p>
        <BotaoLink href="/arenas" className="mt-8">
          Encontrar minha quadra
        </BotaoLink>
      </main>
    </>
  );
}
