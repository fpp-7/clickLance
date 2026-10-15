import Link from "next/link";
import { cn } from "@/lib/utils/cn";

interface Props {
  tamanho?: "sm" | "md" | "lg";
  comTexto?: boolean;
  className?: string;
}

const tamanhos = {
  sm: { icone: "size-7", texto: "text-lg" },
  md: { icone: "size-9", texto: "text-2xl" },
  lg: { icone: "size-14", texto: "text-4xl" },
};

export function IconeMarca({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={className}>
      <circle cx="16" cy="16" r="15" fill="var(--color-primary)" />
      <path d="M12.5 9.5v13l10.5-6.5z" fill="var(--color-on-primary)" />
    </svg>
  );
}

export function Marca({ tamanho = "md", comTexto = true, className }: Props) {
  const t = tamanhos[tamanho];
  return (
    <span className={cn("inline-flex items-center gap-2", className)}>
      <IconeMarca className={cn("shrink-0", t.icone)} />
      {comTexto && (
        <span
          className={cn("font-display leading-none font-bold tracking-wide uppercase", t.texto)}
        >
          Click <span className="text-primary">Lance</span>
        </span>
      )}
    </span>
  );
}

export function LinkMarca(props: Props) {
  return (
    <Link href="/" aria-label="Click Lance, página inicial" className="rounded-md">
      <Marca {...props} />
    </Link>
  );
}
