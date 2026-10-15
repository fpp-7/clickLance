import type {
  Arena,
  ContextoQuadra,
  Lance,
  Partida,
  PedidoDemo,
  ProtocoloDemo,
  Quadra,
  Sessao,
} from "./tipos";

/**
 * Contrato único da API usado por todas as telas.
 *
 * Existem duas implementações: `mock` (dados fictícios, sem backend) e `http`
 * (API real). A escolha é feita em `./index.ts` por variável de ambiente, então
 * trocar uma pela outra não exige mexer em componente nenhum.
 */
export interface ClickLanceApi {
  arenas: {
    /** Busca por nome, cidade ou bairro. Sem termo, retorna todas. */
    buscar(termo?: string): Promise<Arena[]>;
    obterPorSlug(slug: string): Promise<Arena>;
    obter(id: string): Promise<Arena>;
  };

  quadras: {
    listarPorArena(arenaId: string): Promise<Quadra[]>;
    obter(id: string): Promise<Quadra>;
    /** Usado pelo deep link do QR code: resolve arena, quadra e partida atual de uma vez. */
    obterContexto(id: string): Promise<ContextoQuadra>;
  };

  partidas: {
    /** Partidas da quadra, da mais recente para a mais antiga, nos últimos `dias` dias. */
    listarPorQuadra(quadraId: string, opcoes?: { dias?: number }): Promise<Partida[]>;
    obter(id: string): Promise<Partida>;
  };

  lances: {
    /** Lances em ordem cronológica. Com `desde`, só os que aconteceram depois desse instante. */
    listarPorPartida(partidaId: string, opcoes?: { desde?: string }): Promise<Lance[]>;
    obter(id: string): Promise<Lance>;
  };

  auth: {
    enviarCodigo(telefone: string): Promise<{ expiraEm: string }>;
    confirmarCodigo(telefone: string, codigo: string): Promise<Sessao>;
    sessaoAtual(): Promise<Sessao | null>;
    sair(): Promise<void>;
  };

  favoritos: {
    listar(): Promise<Lance[]>;
    listarIds(): Promise<string[]>;
    adicionar(lanceId: string): Promise<void>;
    remover(lanceId: string): Promise<void>;
  };

  comercial: {
    solicitarDemo(pedido: PedidoDemo): Promise<ProtocoloDemo>;
  };
}
