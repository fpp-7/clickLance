/**
 * Tipos de domínio da plataforma. Seguem as entidades do documento de visão
 * (Empresa/Arena, Quadra, Partida, Evento/Lance, Usuário) com os nomes que
 * usamos no dia a dia.
 *
 * Datas são sempre strings ISO 8601 para trafegar sem perda entre mock, API e cache.
 */

export type Modalidade = "society" | "futsal";

export type StatusQuadra = "online" | "offline" | "manutencao";

export type StatusPartida = "ao_vivo" | "encerrada";

export type StatusLance = "processando" | "disponivel" | "expirado";

export interface Arena {
  id: string;
  slug: string;
  nome: string;
  cidade: string;
  uf: string;
  bairro: string;
  logoUrl: string | null;
  /** Cor de destaque escolhida pela arena. Entra na personalização do painel (fase 2). */
  corPrimaria: string | null;
  totalQuadras: number;
}

export interface Quadra {
  id: string;
  arenaId: string;
  nome: string;
  modalidade: Modalidade;
  status: StatusQuadra;
}

/**
 * Uma partida é a faixa de uma hora cheia da quadra (ex.: 20h às 21h).
 * É assim que o jogador localiza o jogo dele, como previsto no documento de visão.
 */
export interface Partida {
  id: string;
  quadraId: string;
  inicio: string;
  fim: string;
  status: StatusPartida;
  totalLances: number;
}

export interface Lance {
  id: string;
  partidaId: string;
  quadraId: string;
  arenaId: string;
  /** Instante em que o botão foi apertado na quadra. */
  momento: string;
  duracaoSegundos: number;
  videoUrl: string;
  thumbnailUrl: string | null;
  expiraEm: string;
  status: StatusLance;
}

export interface Usuario {
  id: string;
  telefone: string;
  nome: string | null;
  criadoEm: string;
}

export interface Sessao {
  token: string;
  usuario: Usuario;
}

/** Tudo que a tela do QR code precisa em uma única chamada (sinal ruim na quadra). */
export interface ContextoQuadra {
  arena: Arena;
  quadra: Quadra;
  partidaAtual: Partida | null;
}

export interface PedidoDemo {
  nomeArena: string;
  responsavel: string;
  telefone: string;
  email: string;
  cidade: string;
  quantidadeQuadras: number;
  mensagem?: string;
}

export interface ProtocoloDemo {
  protocolo: string;
}
