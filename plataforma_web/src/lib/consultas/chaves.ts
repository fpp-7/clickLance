/**
 * Chaves do TanStack Query, centralizadas para invalidação e prefetch
 * usarem sempre o mesmo formato.
 */
export const chaves = {
  arenas: {
    busca: (termo: string) => ["arenas", "busca", termo] as const,
    porSlug: (slug: string) => ["arenas", "slug", slug] as const,
    obter: (id: string) => ["arenas", "id", id] as const,
  },
  quadras: {
    porArena: (arenaId: string) => ["quadras", "arena", arenaId] as const,
    obter: (id: string) => ["quadras", "id", id] as const,
    contexto: (id: string) => ["quadras", "contexto", id] as const,
  },
  partidas: {
    porQuadra: (quadraId: string) => ["partidas", "quadra", quadraId] as const,
    obter: (id: string) => ["partidas", "id", id] as const,
  },
  lances: {
    porPartida: (partidaId: string) => ["lances", "partida", partidaId] as const,
    obter: (id: string) => ["lances", "id", id] as const,
  },
  auth: {
    sessao: ["auth", "sessao"] as const,
  },
  favoritos: {
    lista: ["favoritos", "lista"] as const,
    ids: ["favoritos", "ids"] as const,
  },
};
