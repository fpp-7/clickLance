import { beforeEach, describe, expect, it } from "vitest";
import { ErroApi } from "../erros";
import { CODIGO_SMS_MOCK, criarApiMock } from "./index";

// Segunda-feira, 5 de outubro de 2026, 20:10 no horário local
const INICIO_TESTE = new Date(2026, 9, 5, 20, 10, 0);

function montarApi() {
  let agora = INICIO_TESTE;
  const api = criarApiMock({ latenciaMs: 0, agora: () => agora });
  const avancar = (ms: number) => {
    agora = new Date(agora.getTime() + ms);
  };
  return { api, avancar };
}

async function esperarErro(promessa: Promise<unknown>): Promise<ErroApi> {
  try {
    await promessa;
  } catch (erro) {
    if (erro instanceof ErroApi) return erro;
    throw erro;
  }
  throw new Error("Esperava um ErroApi, mas a chamada deu certo");
}

beforeEach(() => {
  window.localStorage.clear();
});

describe("arenas", () => {
  it("lista todas sem termo e filtra por cidade ignorando acento", async () => {
    const { api } = montarApi();
    expect(await api.arenas.buscar()).toHaveLength(6);

    const porCidade = await api.arenas.buscar("curitiba");
    expect(porCidade.map((a) => a.nome)).toEqual(["Ginásio Futsal Central"]);

    const semAcento = await api.arenas.buscar("ginasio");
    expect(semAcento).toHaveLength(1);
  });

  it("falha com nao_encontrado para slug inexistente", async () => {
    const { api } = montarApi();
    const erro = await esperarErro(api.arenas.obterPorSlug("nao-existe"));
    expect(erro.codigo).toBe("nao_encontrado");
  });
});

describe("quadras e partidas", () => {
  it("resolve o contexto do QR code com a partida da hora atual ao vivo", async () => {
    const { api } = montarApi();
    const contexto = await api.quadras.obterContexto("a1-q1");

    expect(contexto.arena.slug).toBe("arena-bola-na-rede");
    expect(contexto.quadra.nome).toBe("Quadra 1");
    expect(contexto.partidaAtual?.status).toBe("ao_vivo");
    expect(new Date(contexto.partidaAtual!.inicio).getHours()).toBe(20);
    expect(new Date(contexto.partidaAtual!.inicio).getMinutes()).toBe(0);
  });

  it("lista partidas da mais recente para a mais antiga, com a atual ao vivo", async () => {
    const { api } = montarApi();
    const partidas = await api.partidas.listarPorQuadra("a1-q1");

    expect(partidas[0].status).toBe("ao_vivo");
    expect(partidas.slice(1).every((p) => p.status === "encerrada")).toBe(true);
    for (let i = 1; i < partidas.length; i++) {
      expect(partidas[i - 1].inicio > partidas[i].inicio).toBe(true);
    }
  });
});

describe("lances", () => {
  it("gera sempre os mesmos lances para a mesma partida", async () => {
    const primeira = await montarApi().api.lances.listarPorPartida("a1-q1-2026100419");
    const segunda = await montarApi().api.lances.listarPorPartida("a1-q1-2026100419");

    expect(primeira.length).toBeGreaterThan(0);
    expect(primeira).toEqual(segunda);
    expect(primeira.every((l) => l.status === "disponivel")).toBe(true);
  });

  it("marca como expirado lance mais velho que a retenção", async () => {
    const { api } = montarApi();
    // 8 dias antes do teste
    const lances = await api.lances.listarPorPartida("a1-q1-2026092720");
    expect(lances.length).toBeGreaterThan(0);
    expect(lances.every((l) => l.status === "expirado")).toBe(true);
  });

  it("faz aparecer lances novos enquanto a partida ao vivo está aberta", async () => {
    const { api, avancar } = montarApi();
    const partida = (await api.quadras.obterContexto("a1-q1")).partidaAtual!;

    const iniciais = await api.lances.listarPorPartida(partida.id);
    expect(iniciais.every((l) => l.momento <= INICIO_TESTE.toISOString())).toBe(true);

    avancar(16_000);
    const depois = await api.lances.listarPorPartida(partida.id);
    expect(depois).toHaveLength(iniciais.length + 1);
    expect(depois.at(-1)?.status).toBe("processando");

    avancar(10_000);
    const liberado = await api.lances.listarPorPartida(partida.id);
    expect(liberado.at(-1)?.status).toBe("disponivel");

    const ultimoInicial = iniciais.at(-1);
    if (ultimoInicial) {
      const somenteNovos = await api.lances.listarPorPartida(partida.id, {
        desde: ultimoInicial.momento,
      });
      expect(somenteNovos).toHaveLength(1);
    }
  });

  it("obtém um lance pelo id e falha para id desconhecido", async () => {
    const { api } = montarApi();
    const [lance] = await api.lances.listarPorPartida("a1-q1-2026100419");
    expect(await api.lances.obter(lance.id)).toEqual(lance);

    const erro = await esperarErro(api.lances.obter("a1-q1-2026100419-l999"));
    expect(erro.codigo).toBe("nao_encontrado");
  });
});

describe("auth por SMS", () => {
  it("rejeita celular sem DDD", async () => {
    const { api } = montarApi();
    const erro = await esperarErro(api.auth.enviarCodigo("99999"));
    expect(erro.codigo).toBe("dados_invalidos");
  });

  it("entra com o código do mock e mantém a sessão", async () => {
    const { api } = montarApi();
    await api.auth.enviarCodigo("(11) 99999-8888");

    const errado = await esperarErro(api.auth.confirmarCodigo("11999998888", "000000"));
    expect(errado.codigo).toBe("codigo_invalido");

    const sessao = await api.auth.confirmarCodigo("11999998888", CODIGO_SMS_MOCK);
    expect(sessao.usuario.telefone).toBe("11999998888");
    expect(await api.auth.sessaoAtual()).toEqual(sessao);

    await api.auth.sair();
    expect(await api.auth.sessaoAtual()).toBeNull();
  });

  it("expira o código depois de cinco minutos", async () => {
    const { api, avancar } = montarApi();
    await api.auth.enviarCodigo("11999998888");
    avancar(6 * 60_000);
    const erro = await esperarErro(api.auth.confirmarCodigo("11999998888", CODIGO_SMS_MOCK));
    expect(erro.codigo).toBe("expirado");
  });
});

describe("favoritos", () => {
  it("adiciona, lista e remove", async () => {
    const { api } = montarApi();
    const [lance] = await api.lances.listarPorPartida("a1-q1-2026100419");

    await api.favoritos.adicionar(lance.id);
    expect(await api.favoritos.listarIds()).toEqual([lance.id]);
    expect((await api.favoritos.listar()).map((l) => l.id)).toEqual([lance.id]);

    await api.favoritos.remover(lance.id);
    expect(await api.favoritos.listarIds()).toEqual([]);
  });
});

describe("comercial", () => {
  it("valida campos obrigatórios e devolve protocolo", async () => {
    const { api } = montarApi();
    const pedido = {
      nomeArena: "Arena Teste",
      responsavel: "Fulano",
      telefone: "11999998888",
      email: "fulano@arena.com",
      cidade: "São Paulo",
      quantidadeQuadras: 2,
    };

    const erro = await esperarErro(api.comercial.solicitarDemo({ ...pedido, email: "" }));
    expect(erro.codigo).toBe("dados_invalidos");

    const { protocolo } = await api.comercial.solicitarDemo(pedido);
    expect(protocolo).toMatch(/^DEMO-20261005-\d{4}$/);
  });
});
