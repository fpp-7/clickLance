import { Cabecalho } from "@/components/layout/Cabecalho";
import { Rodape } from "@/components/layout/Rodape";

/** Páginas públicas voltadas para donos de quadra: landing, planos, contato. */
export default function LayoutInstitucional({ children }: LayoutProps<"/">) {
  return (
    <>
      <Cabecalho />
      <main className="flex-1">{children}</main>
      <Rodape />
    </>
  );
}
