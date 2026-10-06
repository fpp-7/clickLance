"use client";

import { Search, X } from "lucide-react";
import { useId } from "react";
import { cn } from "@/lib/utils/cn";

interface Props {
  valor: string;
  aoMudar: (valor: string) => void;
  rotulo: string;
  placeholder?: string;
  autoFocus?: boolean;
  className?: string;
}

export function CampoBusca({ valor, aoMudar, rotulo, placeholder, autoFocus, className }: Props) {
  const id = useId();
  return (
    <div className={cn("relative", className)}>
      <label htmlFor={id} className="sr-only">
        {rotulo}
      </label>
      <Search
        aria-hidden="true"
        className="text-muted pointer-events-none absolute top-1/2 left-4 size-5 -translate-y-1/2"
      />
      <input
        id={id}
        type="search"
        inputMode="search"
        autoComplete="off"
        autoFocus={autoFocus}
        value={valor}
        placeholder={placeholder}
        onChange={(evento) => aoMudar(evento.target.value)}
        className="border-border bg-surface text-foreground placeholder:text-muted/70 focus:border-primary h-12 w-full rounded-full border pr-12 pl-12 text-base focus:outline-none [&::-webkit-search-cancel-button]:hidden"
      />
      {valor && (
        <button
          type="button"
          onClick={() => aoMudar("")}
          aria-label="Limpar busca"
          className="text-muted hover:bg-surface-2 hover:text-foreground absolute top-1/2 right-2 flex size-9 -translate-y-1/2 items-center justify-center rounded-full"
        >
          <X className="size-5" aria-hidden="true" />
        </button>
      )}
    </div>
  );
}
