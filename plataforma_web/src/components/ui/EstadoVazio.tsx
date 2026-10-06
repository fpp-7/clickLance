import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

interface Props {
  icone: LucideIcon;
  titulo: string;
  descricao?: string;
  acao?: ReactNode;
  className?: string;
}

export function EstadoVazio({ icone: Icone, titulo, descricao, acao, className }: Props) {
  return (
    <div
      className={cn(
        "rounded-card border-border flex flex-col items-center border border-dashed px-6 py-10 text-center",
        className,
      )}
    >
      <span className="bg-surface-2 text-muted flex size-14 items-center justify-center rounded-full">
        <Icone className="size-7" aria-hidden="true" />
      </span>
      <p className="mt-4 font-semibold">{titulo}</p>
      {descricao && <p className="text-muted mt-1 max-w-xs text-sm">{descricao}</p>}
      {acao && <div className="mt-5">{acao}</div>}
    </div>
  );
}
