import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { chaves } from "./chaves";

export function useArenas(termo = "") {
  return useQuery({
    queryKey: chaves.arenas.busca(termo.trim()),
    queryFn: () => api.arenas.buscar(termo.trim()),
    placeholderData: (anterior) => anterior,
  });
}

export function useArenaPorSlug(slug: string) {
  return useQuery({
    queryKey: chaves.arenas.porSlug(slug),
    queryFn: () => api.arenas.obterPorSlug(slug),
    enabled: Boolean(slug),
  });
}
