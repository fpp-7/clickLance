import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { chaves } from "./chaves";

export function useQuadrasDaArena(arenaId: string | undefined) {
  return useQuery({
    queryKey: chaves.quadras.porArena(arenaId ?? ""),
    queryFn: () => api.quadras.listarPorArena(arenaId as string),
    enabled: Boolean(arenaId),
  });
}
