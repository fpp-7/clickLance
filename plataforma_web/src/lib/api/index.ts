import type { ClickLanceApi } from "./cliente";
import { criarApiHttp } from "./http";
import { criarApiMock } from "./mock";

export type ModoApi = "mock" | "http";

/** Definido em build pelo NEXT_PUBLIC_API_MODE. Sem valor, fica no mock. */
export const modoApi: ModoApi = process.env.NEXT_PUBLIC_API_MODE === "http" ? "http" : "mock";

/**
 * Instância única da API usada pelas telas. Para apontar para a API real,
 * basta `NEXT_PUBLIC_API_MODE=http` e `NEXT_PUBLIC_API_URL` no .env.
 */
export const api: ClickLanceApi =
  modoApi === "http"
    ? criarApiHttp({ baseUrl: process.env.NEXT_PUBLIC_API_URL ?? "/api" })
    : criarApiMock();

export type { ClickLanceApi } from "./cliente";
export * from "./erros";
export * from "./tipos";
