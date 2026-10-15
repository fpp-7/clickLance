/**
 * Acesso ao localStorage com proteção para SSR e para navegadores que bloqueiam
 * armazenamento (modo anônimo, por exemplo). Qualquer falha vira o valor padrão.
 */

const PREFIXO = "clicklance:";

export function lerArmazenado<T>(chave: string, padrao: T): T {
  if (typeof window === "undefined") return padrao;
  try {
    const bruto = window.localStorage.getItem(PREFIXO + chave);
    return bruto === null ? padrao : (JSON.parse(bruto) as T);
  } catch {
    return padrao;
  }
}

export function gravarArmazenado(chave: string, valor: unknown): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(PREFIXO + chave, JSON.stringify(valor));
  } catch {
    // sem espaço ou sem permissão: segue sem persistir
  }
}

export function removerArmazenado(chave: string): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.removeItem(PREFIXO + chave);
  } catch {
    // idem
  }
}
