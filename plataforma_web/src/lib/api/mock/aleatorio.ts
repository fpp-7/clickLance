/**
 * Gerador pseudoaleatório com semente (mulberry32). O mock precisa produzir
 * sempre os mesmos dados para o mesmo id, senão a lista de lances muda a cada
 * refetch e o link compartilhado deixa de bater com o que a pessoa viu.
 */

export interface Aleatorio {
  /** Número em [0, 1). */
  proximo(): number;
  /** Inteiro entre `min` e `max`, ambos inclusos. */
  entre(min: number, max: number): number;
  escolher<T>(itens: readonly T[]): T;
}

export function criarAleatorio(semente: number): Aleatorio {
  let estado = semente >>> 0 || 1;

  const proximo = () => {
    estado = (estado + 0x6d2b79f5) >>> 0;
    let t = estado;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };

  return {
    proximo,
    entre: (min, max) => min + Math.floor(proximo() * (max - min + 1)),
    escolher: (itens) => itens[Math.floor(proximo() * itens.length)],
  };
}

/** Hash FNV-1a de 32 bits, suficiente para transformar um id em semente. */
export function hashTexto(texto: string): number {
  let hash = 2166136261;
  for (let i = 0; i < texto.length; i++) {
    hash ^= texto.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}
