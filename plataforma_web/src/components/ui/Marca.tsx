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
      <rect x="1" y="1" width="30" height="30" rx="9" fill="var(--color-primary)" />
      <path d="m12 9 11 7-11 7V9Z" fill="white" />
      <path d="M7 11v10" stroke="#e1b750" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

export function Marca({ tamanho = "md", comTexto = true, className }: Props) {
  const t = tamanhos[tamanho];
  return (
    <span className={cn("inline-flex items-center gap-2", className)}>
      <IconeMarca className={cn("shrink-0", t.icone)} />
      {comTexto && <span className={cn("brand-word", t.texto)}>clicklance</span>}
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
