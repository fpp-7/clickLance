import type { Violation } from "./types";

export class ApiError extends Error {
  constructor(
    message: string,
    public status = 0,
    public code = "NETWORK_ERROR",
    public correlationId?: string,
    public violations: Violation[] = [],
    public retryAfter?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function errorMessage(status: number, code?: string) {
  if (code === "LIBRARY_LIMIT_REACHED")
    return "Sua biblioteca chegou a 100 lances. Remova um para salvar outro.";
  if (code === "INVALID_RESET_TOKEN")
    return "O código de recuperação expirou ou já foi usado. Solicite outro.";
  if (code === "PURGE_DISABLED")
    return "A limpeza está desativada. Configure a operação no servidor antes de executar.";
  if (code === "CONFIG_VERSION_CONFLICT")
    return "A configuração foi alterada. Atualize os dados e tente novamente.";
  if (status === 401) return "Entre novamente para continuar. Sua sessão pode ter terminado.";
  if (status === 403) return "Sua conta não tem permissão para acessar este conteúdo.";
  if (status === 404)
    return "Este conteúdo não está disponível. Ele pode ter expirado ou sido retirado.";
  if (status === 409)
    return "Estes dados já estão em uso ou foram alterados. Confira e tente novamente.";
  if (status === 429)
    return "Muitas tentativas em pouco tempo. Aguarde um minuto e tente novamente.";
  if (status === 400 || status === 422) return "Confira os campos informados e tente novamente.";
  if (status === 503)
    return "O serviço está temporariamente indisponível. Tente novamente em instantes.";
  return "Não foi possível concluir a solicitação. Tente novamente.";
}
