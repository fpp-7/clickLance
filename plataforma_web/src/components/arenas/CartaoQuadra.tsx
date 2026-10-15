import type { Quadra, StatusQuadra } from "@/lib/api";
import { cn } from "@/lib/utils/cn";

const status: Record<StatusQuadra, { rotulo: string; cor: string }> = {
  online: { rotulo: "Gravando", cor: "bg-success" },
  offline: { rotulo: "Sem câmera", cor: "bg-danger" },
  manutencao: { rotulo: "Em manutenção", cor: "bg-warning" },
};

const modalidades = {
  society: "Society",
  futsal: "Futsal",
};

export function CartaoQuadra({ quadra }: { quadra: Quadra }) {
  const estado = status[quadra.status];
  return (
    <div className="rounded-card border-border bg-surface flex items-center gap-4 border p-4">
      <span className="min-w-0 flex-1">
        <span className="block truncate font-semibold">{quadra.nome}</span>
        <span className="text-muted mt-0.5 block text-sm">{modalidades[quadra.modalidade]}</span>
      </span>
      <span className="text-muted flex shrink-0 items-center gap-2 text-sm">
        <span aria-hidden="true" className={cn("size-2 rounded-full", estado.cor)} />
        {estado.rotulo}
      </span>
    </div>
  );
}
