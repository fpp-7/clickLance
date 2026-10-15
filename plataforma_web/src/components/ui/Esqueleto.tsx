import { cn } from "@/lib/utils/cn";

/** Bloco cinza pulsando usado enquanto o conteúdo carrega. */
export function Esqueleto({ className }: { className?: string }) {
  return (
    <div aria-hidden="true" className={cn("bg-surface-2 animate-pulse rounded-lg", className)} />
  );
}
