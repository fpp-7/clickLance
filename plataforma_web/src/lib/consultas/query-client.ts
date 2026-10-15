import { QueryClient } from "@tanstack/react-query";
import { ehErroApi } from "@/lib/api";

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
            ehErroApi(erro) &&
            ["nao_encontrado", "nao_autorizado", "expirado"].includes(erro.codigo)
          ) {
            return false;
          }
          return tentativa < 1;
        },
      },
    },
  });
}
