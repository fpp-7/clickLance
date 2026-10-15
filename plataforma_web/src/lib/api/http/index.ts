import type { ClickLanceApi } from "../cliente";
import { ErroApi, type CodigoErroApi } from "../erros";
import type { Sessao } from "../tipos";
import { gravarArmazenado, lerArmazenado, removerArmazenado } from "../mock/armazenamento";

/**
 * Implementação contra a API real (Spring Boot).
 *
 * Os caminhos abaixo são a proposta de contrato para o backend. Quando a API
 * existir, é aqui que a gente ajusta rota e formato de resposta; as telas
 * continuam falando só com a interface `ClickLanceApi`.
 */

const CHAVE_SESSAO = "sessao";

export interface OpcoesHttp {
  baseUrl: string;
}

function codigoPorStatus(status: number): CodigoErroApi {
  if (status === 401 || status === 403) return "nao_autorizado";
  if (status === 404) return "nao_encontrado";
  if (status === 410) return "expirado";
  if (status === 400 || status === 422) return "dados_invalidos";
  if (status >= 500) return "servidor";
  return "desconhecido";
}

export function criarApiHttp({ baseUrl }: OpcoesHttp): ClickLanceApi {
  const base = baseUrl.replace(/\/$/, "");

  async function requisitar<T>(caminho: string, init: RequestInit = {}): Promise<T> {
    const sessao = lerArmazenado<Sessao | null>(CHAVE_SESSAO, null);
    const cabecalhos = new Headers(init.headers);
    cabecalhos.set("Accept", "application/json");
    if (init.body) cabecalhos.set("Content-Type", "application/json");
    if (sessao) cabecalhos.set("Authorization", `Bearer ${sessao.token}`);

    let resposta: Response;
    try {
      resposta = await fetch(`${base}${caminho}`, { ...init, headers: cabecalhos });
    } catch {
      throw new ErroApi("rede", "Sem conexão com o servidor");
    }

    if (!resposta.ok) {
      let mensagem = resposta.statusText || "Erro na requisição";
      try {
        const corpo = (await resposta.json()) as { mensagem?: string; message?: string };
        mensagem = corpo.mensagem ?? corpo.message ?? mensagem;
      } catch {
        // corpo sem JSON, fica com o statusText
      }
      throw new ErroApi(codigoPorStatus(resposta.status), mensagem, resposta.status);
    }

    if (resposta.status === 204) return undefined as T;
    return (await resposta.json()) as T;
  }

  const consulta = (parametros: Record<string, string | number | undefined>) => {
    const query = new URLSearchParams();
    for (const [chave, valor] of Object.entries(parametros)) {
      if (valor !== undefined && valor !== "") query.set(chave, String(valor));
    }
    const texto = query.toString();
    return texto ? `?${texto}` : "";
  };

  return {
    arenas: {
      buscar: (termo) => requisitar(`/arenas${consulta({ busca: termo })}`),
      obterPorSlug: (slug) => requisitar(`/arenas/slug/${encodeURIComponent(slug)}`),
      obter: (id) => requisitar(`/arenas/${encodeURIComponent(id)}`),
    },
    quadras: {
      listarPorArena: (arenaId) => requisitar(`/arenas/${encodeURIComponent(arenaId)}/quadras`),
      obter: (id) => requisitar(`/quadras/${encodeURIComponent(id)}`),
      obterContexto: (id) => requisitar(`/quadras/${encodeURIComponent(id)}/contexto`),
    },
    partidas: {
      listarPorQuadra: (quadraId, opcoes) =>
        requisitar(
          `/quadras/${encodeURIComponent(quadraId)}/partidas${consulta({ dias: opcoes?.dias })}`,
        ),
      obter: (id) => requisitar(`/partidas/${encodeURIComponent(id)}`),
    },
    lances: {
      listarPorPartida: (partidaId, opcoes) =>
        requisitar(
          `/partidas/${encodeURIComponent(partidaId)}/lances${consulta({ desde: opcoes?.desde })}`,
        ),
      obter: (id) => requisitar(`/lances/${encodeURIComponent(id)}`),
    },
    auth: {
      enviarCodigo: (telefone) =>
        requisitar("/auth/sms", { method: "POST", body: JSON.stringify({ telefone }) }),
      async confirmarCodigo(telefone, codigo) {
        const sessao = await requisitar<Sessao>("/auth/sms/confirmar", {
          method: "POST",
          body: JSON.stringify({ telefone, codigo }),
        });
        gravarArmazenado(CHAVE_SESSAO, sessao);
        return sessao;
      },
      async sessaoAtual() {
        const sessao = lerArmazenado<Sessao | null>(CHAVE_SESSAO, null);
        if (!sessao) return null;
        try {
          return await requisitar<Sessao>("/auth/sessao");
        } catch (erro) {
          if (erro instanceof ErroApi && erro.codigo === "nao_autorizado") {
            removerArmazenado(CHAVE_SESSAO);
            return null;
          }
          throw erro;
        }
      },
      async sair() {
        try {
          await requisitar("/auth/sair", { method: "POST" });
        } finally {
          removerArmazenado(CHAVE_SESSAO);
        }
      },
    },
    favoritos: {
      listar: () => requisitar("/favoritos"),
      listarIds: () => requisitar("/favoritos/ids"),
      adicionar: (lanceId) =>
        requisitar(`/favoritos/${encodeURIComponent(lanceId)}`, { method: "PUT" }),
      remover: (lanceId) =>
        requisitar(`/favoritos/${encodeURIComponent(lanceId)}`, { method: "DELETE" }),
    },
    comercial: {
      solicitarDemo: (pedido) =>
        requisitar("/comercial/demonstracoes", { method: "POST", body: JSON.stringify(pedido) }),
    },
  };
}
