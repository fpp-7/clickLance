import Link from "next/link";
import { Loader2 } from "lucide-react";
import type { ButtonHTMLAttributes, ComponentProps } from "react";
import { cn } from "@/lib/utils/cn";

export type VarianteBotao = "primario" | "secundario" | "fantasma" | "perigo";
export type TamanhoBotao = "sm" | "md" | "lg";

interface EstiloBotao {
  variante?: VarianteBotao;
  tamanho?: TamanhoBotao;
  largura?: "auto" | "total";
}

const variantes: Record<VarianteBotao, string> = {
  primario: "bg-primary text-on-primary hover:bg-primary-hover",
  secundario: "border border-border bg-surface-2 text-foreground hover:border-muted/60",
  fantasma: "text-foreground hover:bg-surface-2",
  perigo: "bg-danger/15 text-danger hover:bg-danger/25",
};

// Alturas a partir de 44px para o toque no celular ser confortável
const tamanhos: Record<TamanhoBotao, string> = {
  sm: "h-10 px-4 text-sm",
  md: "h-11 px-5 text-sm",
  lg: "h-13 px-7 text-base",
};

export function classesBotao({
  variante = "primario",
  tamanho = "md",
  largura = "auto",
}: EstiloBotao = {}): string {
  return cn(
    "inline-flex items-center justify-center gap-2 rounded-full font-semibold whitespace-nowrap transition select-none active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50",
    variantes[variante],
    tamanhos[tamanho],
    largura === "total" && "w-full",
  );
}

type PropsBotao = ButtonHTMLAttributes<HTMLButtonElement> &
  EstiloBotao & {
    carregando?: boolean;
  };

export function Botao({
  variante,
  tamanho,
  largura,
  carregando = false,
  disabled,
  className,
  children,
  type = "button",
  ...resto
}: PropsBotao) {
  return (
    <button
      type={type}
      disabled={disabled || carregando}
      aria-busy={carregando || undefined}
      className={cn(classesBotao({ variante, tamanho, largura }), className)}
      {...resto}
    >
      {carregando && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  );
}

type PropsBotaoLink = ComponentProps<typeof Link> & EstiloBotao;

export function BotaoLink({ variante, tamanho, largura, className, ...resto }: PropsBotaoLink) {
  return (
    <Link className={cn(classesBotao({ variante, tamanho, largura }), className)} {...resto} />
  );
}
