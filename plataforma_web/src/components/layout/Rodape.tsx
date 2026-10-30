import Link from "next/link";
import { Marca } from "@/components/ui/Marca";
export function Rodape() {
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <Marca tamanho="sm" />
        <p>O futebol é seu. A memória também.</p>
        <nav aria-label="Rodapé">
          <Link href="/arenas">Encontrar quadra</Link>
          <Link href="/admin/entrar">Área da arena</Link>
        </nav>
      </div>
    </footer>
  );
}
