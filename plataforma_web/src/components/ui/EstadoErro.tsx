"use client";

import { AlertTriangle, SearchX, WifiOff } from "lucide-react";
import { ApiError } from "@/lib/platform/api";
import { Botao } from "./Botao";
import { EstadoVazio } from "./EstadoVazio";

interface Props {
  erro: unknown;
  aoTentarDeNovo?: () => void;
  tentando?: boolean;
  className?: string;
}

const mensagens: Record<string, { titulo: string; descricao: string }> = {
  rede: {
    titulo: "Sem conexão",
    descricao: "Não deu para falar com o servidor. Confira o sinal e tente de novo.",
  },
  nao_encontrado: {
    titulo: "Não encontramos isso",
    descricao: "O link pode estar errado ou o conteúdo não está mais disponível.",
  },
  nao_autorizado: {
    titulo: "Você precisa entrar",
    descricao: "Entre com seu email e senha para continuar.",
  },
};

const padrao = {
  titulo: "Algo deu errado",
  descricao: "Tivemos um problema ao carregar. Tente de novo em instantes.",
};

export function EstadoErro({ erro, aoTentarDeNovo, tentando, className }: Props) {
  const codigo =
    erro instanceof ApiError
      ? erro.status === 401 || erro.status === 403
        ? "nao_autorizado"
        : erro.status === 404
          ? "nao_encontrado"
          : erro.status
            ? "servidor"
            : "rede"
      : "desconhecido";
  const texto = mensagens[codigo] ?? padrao;
  const Icone = codigo === "rede" ? WifiOff : codigo === "nao_encontrado" ? SearchX : AlertTriangle;

  return (
    <div role="alert" className={className}>
      <EstadoVazio
        icone={Icone}
        titulo={texto.titulo}
        descricao={texto.descricao}
        acao={
          aoTentarDeNovo &&
          codigo !== "nao_encontrado" && (
            <Botao variante="secundario" onClick={aoTentarDeNovo} carregando={tentando}>
              Tentar de novo
            </Botao>
          )
        }
      />
    </div>
  );
}
