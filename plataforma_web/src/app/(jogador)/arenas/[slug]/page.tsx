import type { Metadata } from "next";
import { DetalheArena } from "@/components/arenas/DetalheArena";
import { api } from "@/lib/api";

type Props = PageProps<"/arenas/[slug]">;

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  try {
    const arena = await api.arenas.obterPorSlug(slug);
    return { title: arena.nome, description: `Lances das quadras da ${arena.nome}` };
  } catch {
    return { title: "Arena" };
  }
}

export default async function PaginaArena({ params }: Props) {
  const { slug } = await params;
  return <DetalheArena slug={slug} />;
}
