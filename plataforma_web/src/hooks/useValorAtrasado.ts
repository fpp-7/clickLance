import { useEffect, useState } from "react";

/** Devolve o valor só depois que ele para de mudar por `atrasoMs`. Evita buscar a cada tecla. */
export function useValorAtrasado<T>(valor: T, atrasoMs = 300): T {
  const [atrasado, setAtrasado] = useState(valor);

  useEffect(() => {
    const timer = setTimeout(() => setAtrasado(valor), atrasoMs);
    return () => clearTimeout(timer);
  }, [valor, atrasoMs]);

  return atrasado;
}
