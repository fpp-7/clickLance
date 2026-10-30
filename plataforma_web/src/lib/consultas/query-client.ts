import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/platform/api";

/**
 * Padrões pensados para quem abre o site na quadra com sinal fraco:
 * dado recente vale por 30 s, uma tentativa extra em falha de rede e
 * nada de retry para "não encontrado" ou "não autorizado".
 */
export function criarQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        gcTime: 10 * 60_000,
        refetchOnWindowFocus: true,
        retry: (tentativa, erro) => {
          if (
            erro instanceof ApiError &&
            [401, 403, 404, 409, 422, 429].includes(erro.status ?? 0)
          ) {
            return false;
          }
          return tentativa < 1;
        },
      },
    },
  });
}
