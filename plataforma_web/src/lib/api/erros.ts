export type CodigoErroApi =
  | "nao_encontrado"
  | "nao_autorizado"
  | "codigo_invalido"
  | "dados_invalidos"
  | "expirado"
  | "rede"
  | "servidor"
  | "desconhecido";

/**
 * Erro padronizado da camada de API. As telas decidem o que mostrar
 * olhando só o `codigo`, sem depender de como a implementação falhou.
 */
export class ErroApi extends Error {
  readonly codigo: CodigoErroApi;
  readonly status?: number;

  constructor(codigo: CodigoErroApi, mensagem: string, status?: number) {
    super(mensagem);
    this.name = "ErroApi";
    this.codigo = codigo;
    this.status = status;
  }
}

export function ehErroApi(erro: unknown): erro is ErroApi {
  return erro instanceof ErroApi;
}

/** Extrai um código de erro de qualquer coisa que tenha sido lançada. */
export function codigoDoErro(erro: unknown): CodigoErroApi {
  if (ehErroApi(erro)) return erro.codigo;
  // fetch rejeita com TypeError quando não há conexão
  if (erro instanceof TypeError) return "rede";
  return "desconhecido";
}
