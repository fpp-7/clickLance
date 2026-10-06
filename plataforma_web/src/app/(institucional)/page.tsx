import { MousePointerClick, QrCode, Share2 } from "lucide-react";
import { BotaoLink } from "@/components/ui/Botao";

const passos = [
  {
    icone: MousePointerClick,
    titulo: "Aperte o botão",
    texto: "Saiu um lance bonito? Aperte o botão na quadra e os últimos segundos ficam salvos.",
  },
  {
    icone: QrCode,
    titulo: "Escaneie o QR code",
    texto: "Aponte a câmera do celular para o QR code da quadra e veja os lances da sua partida.",
  },
  {
    icone: Share2,
    titulo: "Assista e compartilhe",
    texto: "Veja em câmera lenta, baixe o vídeo e mande para o grupo do time.",
  },
];

export default function PaginaInicial() {
  return (
    <div className="mx-auto w-full max-w-6xl px-4">
      <section className="flex flex-col items-center py-16 text-center sm:py-24">
        <p className="text-primary text-sm font-semibold tracking-widest uppercase">
          Replays para quadras de society e futsal
        </p>
        <h1 className="font-display mt-4 max-w-3xl text-5xl leading-none font-bold tracking-wide uppercase sm:text-7xl">
          Seu lance, gravado <span className="text-primary">com um clique</span>
        </h1>
        <p className="text-muted mt-6 max-w-xl text-lg">
          A câmera da quadra grava o jogo inteiro. Você aperta o botão no momento certo e o lance
          aparece no seu celular para assistir, baixar e compartilhar.
        </p>
        <div className="mt-10 flex w-full flex-col gap-3 sm:w-auto sm:flex-row">
          <BotaoLink href="/arenas" tamanho="lg" largura="total">
            Encontrar minha quadra
          </BotaoLink>
        </div>
      </section>

      <section aria-labelledby="como-funciona" className="pb-20">
        <h2 id="como-funciona" className="sr-only">
          Como funciona
        </h2>
        <ol className="grid gap-4 sm:grid-cols-3">
          {passos.map((passo, i) => (
            <li key={passo.titulo} className="rounded-card border-border bg-surface border p-6">
              <span className="bg-primary/15 text-primary flex size-11 items-center justify-center rounded-full">
                <passo.icone className="size-5" aria-hidden="true" />
              </span>
              <p className="text-muted mt-5 text-sm font-semibold">Passo {i + 1}</p>
              <h3 className="mt-1 text-lg font-semibold">{passo.titulo}</h3>
              <p className="text-muted mt-2 text-sm">{passo.texto}</p>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
