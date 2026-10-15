import { Marca } from "@/components/ui/Marca";

export function Rodape() {
  return (
    <footer className="border-border/60 pb-safe mt-auto border-t">
      <div className="text-muted mx-auto flex w-full max-w-6xl flex-col items-center gap-3 px-4 py-8 text-center text-sm sm:flex-row sm:justify-between sm:text-left">
        <Marca tamanho="sm" />
        <p>Replays para quadras de society e futsal.</p>
      </div>
    </footer>
  );
}
