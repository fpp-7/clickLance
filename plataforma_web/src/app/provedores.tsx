"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { criarQueryClient } from "@/lib/consultas/query-client";

export function Provedores({ children }: { children: ReactNode }) {
  // Um QueryClient por montagem, para o cache não vazar entre requisições no servidor
  const [queryClient] = useState(criarQueryClient);
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
