import type { ClickLanceApi } from "../cliente";
import { ErroApi } from "../erros";
import type { Lance, Partida, PedidoDemo, Quadra, Sessao } from "../tipos";
import { hashTexto } from "./aleatorio";
import { gravarArmazenado, lerArmazenado, removerArmazenado } from "./armazenamento";
import {
  ARENAS,
  DIAS_HISTORICO,
  QUADRAS,
  chaveDaHora,
  decodificarIdLance,
  decodificarIdPartida,
  gerarLancesSemente,
  gerarLancesSimulados,
  gerarPartidas,
  inicioDaHora,
  montarPartida,
} from "./dados";

/** Código que o mock aceita no login por SMS. */
export const CODIGO_SMS_MOCK = "123456";

const VALIDADE_CODIGO_MS = 5 * 60_000;

const CHAVE_SESSAO = "sessao";
const CHAVE_FAVORITOS = "favoritos";
const chaveSimulacao = (partidaId: string) => `mock:simulacao:${partidaId}`;
const chaveCodigo = (telefone: string) => `mock:codigo:${telefone}`;

export interface OpcoesMock {
  /** Latência média de cada chamada. Zero deixa tudo instantâneo (bom para testes). */
  latenciaMs?: number;
  /** Fração de chamadas que falham de propósito, entre 0 e 1. Útil para ver as telas de erro. */
  taxaDeFalha?: number;
  /** Relógio injetável para os testes controlarem a simulação ao vivo. */
  agora?: () => Date;
}

function lerNumeroDoAmbiente(valor: string | undefined, padrao: number): number {
  const numero = Number(valor);
  return valor === undefined || Number.isNaN(numero) ? padrao : numero;
}

function normalizarTexto(texto: string): string {
  return texto.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().trim();
}

export function criarApiMock(opcoes: OpcoesMock = {}): ClickLanceApi {
  const latenciaMs =
    opcoes.latenciaMs ?? lerNumeroDoAmbiente(process.env.NEXT_PUBLIC_MOCK_LATENCIA, 450);
  const taxaDeFalha =
    opcoes.taxaDeFalha ?? lerNumeroDoAmbiente(process.env.NEXT_PUBLIC_MOCK_FALHAS, 0);
  const agora = opcoes.agora ?? (() => new Date());

  // Lances gerados ficam em cache por partida para a lista não "pular" entre refetches.
  const cacheLancesSemente = new Map<string, Lance[]>();

  async function simularRede(): Promise<void> {
    if (latenciaMs > 0) {
      const variacao = 0.7 + Math.random() * 0.6;
      await new Promise((resolver) => setTimeout(resolver, latenciaMs * variacao));
    }
    if (taxaDeFalha > 0 && Math.random() < taxaDeFalha) {
      throw new ErroApi("servidor", "Falha simulada pelo mock", 500);
    }
  }

  // ---------------------------------------------------------------------
  // Helpers de domínio
  // ---------------------------------------------------------------------

  function encontrarQuadra(id: string): Quadra {
    const quadra = QUADRAS.find((q) => q.id === id);
    if (!quadra) throw new ErroApi("nao_encontrado", "Quadra não encontrada", 404);
    return quadra;
  }

  function encontrarArena(id: string) {
    const arena = ARENAS.find((a) => a.id === id);
    if (!arena) throw new ErroApi("nao_encontrado", "Arena não encontrada", 404);
    return arena;
  }

  function reconstruirPartida(id: string): { partida: Partida; quadra: Quadra } {
    const decodificado = decodificarIdPartida(id);
    if (!decodificado) throw new ErroApi("nao_encontrado", "Partida não encontrada", 404);
    const quadra = encontrarQuadra(decodificado.quadraId);
    const partida = montarPartida(quadra, decodificado.inicio, agora());
    if (Date.parse(partida.inicio) > agora().getTime()) {
      throw new ErroApi("nao_encontrado", "Partida não encontrada", 404);
    }
    return { partida, quadra };
  }

  function lancesSemente(partida: Partida, quadra: Quadra): Lance[] {
    const existente = cacheLancesSemente.get(partida.id);
    if (existente) return existente;
    const gerados = gerarLancesSemente(partida, quadra, agora());
    cacheLancesSemente.set(partida.id, gerados);
    return gerados;
  }

  function lancesSimulados(partida: Partida, quadra: Quadra): Lance[] {
    if (partida.status !== "ao_vivo") {
      const inicioSimulacao = lerArmazenado<number | null>(chaveSimulacao(partida.id), null);
      return inicioSimulacao ? gerarLancesSimulados(partida, quadra, inicioSimulacao, agora()) : [];
    }
    let inicioSimulacao = lerArmazenado<number | null>(chaveSimulacao(partida.id), null);
    if (!inicioSimulacao) {
      inicioSimulacao = agora().getTime();
      gravarArmazenado(chaveSimulacao(partida.id), inicioSimulacao);
    }
    return gerarLancesSimulados(partida, quadra, inicioSimulacao, agora());
  }

  function todosOsLances(partida: Partida, quadra: Quadra): Lance[] {
    const momentoAtual = agora().getTime();
    return [...lancesSemente(partida, quadra), ...lancesSimulados(partida, quadra)]
      .filter((lance) => Date.parse(lance.momento) <= momentoAtual)
      .sort((a, b) => a.momento.localeCompare(b.momento));
  }

  function encontrarLance(id: string): Lance {
    const decodificado = decodificarIdLance(id);
    if (!decodificado) throw new ErroApi("nao_encontrado", "Lance não encontrado", 404);
    const { partida, quadra } = reconstruirPartida(decodificado.partidaId);
    const lance = todosOsLances(partida, quadra).find((l) => l.id === id);
    if (!lance) throw new ErroApi("nao_encontrado", "Lance não encontrado", 404);
    return lance;
  }

  function lerSessao(): Sessao | null {
    return lerArmazenado<Sessao | null>(CHAVE_SESSAO, null);
  }

  function lerFavoritos(): string[] {
    return lerArmazenado<string[]>(CHAVE_FAVORITOS, []);
  }

  // ---------------------------------------------------------------------
  // Implementação do contrato
  // ---------------------------------------------------------------------

  return {
    arenas: {
      async buscar(termo) {
        await simularRede();
        const busca = normalizarTexto(termo ?? "");
        if (!busca) return [...ARENAS];
        return ARENAS.filter((arena) =>
          normalizarTexto(`${arena.nome} ${arena.cidade} ${arena.bairro} ${arena.uf}`).includes(
            busca,
          ),
        );
      },
      async obterPorSlug(slug) {
        await simularRede();
        const arena = ARENAS.find((a) => a.slug === slug);
        if (!arena) throw new ErroApi("nao_encontrado", "Arena não encontrada", 404);
        return arena;
      },
      async obter(id) {
        await simularRede();
        return encontrarArena(id);
      },
    },

    quadras: {
      async listarPorArena(arenaId) {
        await simularRede();
        return QUADRAS.filter((q) => q.arenaId === arenaId);
      },
      async obter(id) {
        await simularRede();
        return encontrarQuadra(id);
      },
      async obterContexto(id) {
        await simularRede();
        const quadra = encontrarQuadra(id);
        const arena = encontrarArena(quadra.arenaId);
        const partidaAtual = montarPartida(quadra, inicioDaHora(agora()), agora());
        return { arena, quadra, partidaAtual };
      },
    },

    partidas: {
      async listarPorQuadra(quadraId, { dias = DIAS_HISTORICO } = {}) {
        await simularRede();
        const quadra = encontrarQuadra(quadraId);
        return gerarPartidas(quadra, agora(), dias).map((partida) =>
          partida.status === "ao_vivo"
            ? { ...partida, totalLances: todosOsLances(partida, quadra).length }
            : partida,
        );
      },
      async obter(id) {
        await simularRede();
        const { partida, quadra } = reconstruirPartida(id);
        return { ...partida, totalLances: todosOsLances(partida, quadra).length };
      },
    },

    lances: {
      async listarPorPartida(partidaId, { desde } = {}) {
        await simularRede();
        const { partida, quadra } = reconstruirPartida(partidaId);
        const lances = todosOsLances(partida, quadra);
        return desde ? lances.filter((lance) => lance.momento > desde) : lances;
      },
      async obter(id) {
        await simularRede();
        return encontrarLance(id);
      },
    },

    auth: {
      async enviarCodigo(telefone) {
        await simularRede();
        const digitos = telefone.replace(/\D/g, "");
        if (digitos.length < 10 || digitos.length > 11) {
          throw new ErroApi("dados_invalidos", "Informe um celular com DDD", 400);
        }
        const expiraEm = agora().getTime() + VALIDADE_CODIGO_MS;
        gravarArmazenado(chaveCodigo(digitos), expiraEm);
        return { expiraEm: new Date(expiraEm).toISOString() };
      },
      async confirmarCodigo(telefone, codigo) {
        await simularRede();
        const digitos = telefone.replace(/\D/g, "");
        const expiraEm = lerArmazenado<number | null>(chaveCodigo(digitos), null);
        if (!expiraEm || expiraEm < agora().getTime()) {
          throw new ErroApi("expirado", "Código expirado. Peça um novo.", 410);
        }
        if (codigo.trim() !== CODIGO_SMS_MOCK) {
          throw new ErroApi("codigo_invalido", "Código incorreto", 401);
        }
        removerArmazenado(chaveCodigo(digitos));
        const sessao: Sessao = {
          token: `mock-${hashTexto(digitos).toString(16)}`,
          usuario: {
            id: `u${hashTexto(digitos)}`,
            telefone: digitos,
            nome: null,
            criadoEm: agora().toISOString(),
          },
        };
        gravarArmazenado(CHAVE_SESSAO, sessao);
        return sessao;
      },
      async sessaoAtual() {
        return lerSessao();
      },
      async sair() {
        await simularRede();
        removerArmazenado(CHAVE_SESSAO);
      },
    },

    favoritos: {
      async listarIds() {
        return lerFavoritos();
      },
      async listar() {
        await simularRede();
        const lances: Lance[] = [];
        for (const id of lerFavoritos()) {
          try {
            lances.push(encontrarLance(id));
          } catch {
            // lance que não existe mais (expirou e saiu do histórico) é ignorado
          }
        }
        return lances;
      },
      async adicionar(lanceId) {
        await simularRede();
        encontrarLance(lanceId);
        const atuais = lerFavoritos().filter((id) => id !== lanceId);
        gravarArmazenado(CHAVE_FAVORITOS, [lanceId, ...atuais]);
      },
      async remover(lanceId) {
        await simularRede();
        gravarArmazenado(
          CHAVE_FAVORITOS,
          lerFavoritos().filter((id) => id !== lanceId),
        );
      },
    },

    comercial: {
      async solicitarDemo(pedido: PedidoDemo) {
        await simularRede();
        const obrigatorios: Array<keyof PedidoDemo> = [
          "nomeArena",
          "responsavel",
          "telefone",
          "email",
          "cidade",
        ];
        const faltando = obrigatorios.filter((campo) => !String(pedido[campo] ?? "").trim());
        if (faltando.length > 0 || !(pedido.quantidadeQuadras > 0)) {
          throw new ErroApi("dados_invalidos", "Preencha todos os campos obrigatórios", 400);
        }
        const sufixo = String(1000 + Math.floor(Math.random() * 9000));
        return { protocolo: `DEMO-${chaveDaHora(agora()).slice(0, 8)}-${sufixo}` };
      },
    },
  };
}
