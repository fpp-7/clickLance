import type { Metadata } from "next";
import { BuscaArenas } from "@/components/arenas/BuscaArenas";

export const metadata: Metadata = {
  title: "Encontre sua quadra",
};

export default function PaginaArenas() {
  return (
    <div className="space-y-5">
      <header>
        <h1 className="font-display text-3xl font-bold tracking-wide uppercase">
          Encontre sua quadra
        </h1>
        <p className="text-muted mt-1 text-sm">
          Na quadra, escaneie o QR code para cair direto na sua partida.
        </p>
      </header>
      <BuscaArenas />
    </div>
  );
}
