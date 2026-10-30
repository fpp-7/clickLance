"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ApiError, request } from "@/lib/platform/api";
import type { Page } from "@/lib/platform/types";
import { useSession } from "@/lib/platform/session";
import s from "./admin.module.css";

export function useAdminQuery<T>(path: string | null) {
  const { user } = useSession("admin");
  return useQuery({
    queryKey: ["admin", user?.usuario_id, path],
    queryFn: () => request<T>(path!, { role: "admin" }),
    enabled: !!path && !!user,
    retry: false,
  });
}
export function errorText(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === "PURGE_DISABLED")
      return "A limpeza está desativada. O operador precisa configurar o storage e habilitar a rotina no servidor.";
    if (error.code === "CONFIG_VERSION_CONFLICT")
      return "A versão base não corresponde à configuração atual. Confira a versão com o responsável da instalação antes de publicar novamente.";
    if (error.status === 401) return "Sua sessão encerrou. Entre novamente para continuar.";
    if (error.status === 403) return "Sua conta não tem permissão para esta operação.";
    if (error.status === 409)
      return "Os dados mudaram ou já estão cadastrados. Confira antes de tentar novamente.";
    if (error.status === 429)
      return "Muitas solicitações em pouco tempo. Aguarde um minuto e tente novamente.";
    if (error.status === 503)
      return "Esta operação está indisponível. Confira a configuração do serviço e tente novamente.";
    return error.message || "Não foi possível concluir a operação.";
  }
  return "Não foi possível conectar. Confira sua conexão e tente novamente.";
}
export function ErrorNotice({ error }: { error: unknown }) {
  if (!error) return null;
  const labels: Record<string, string> = {
    nome: "Nome",
    timezone: "Fuso horário",
    bucket: "Bucket autorizado",
    dispositivo_id: "Identificador do dispositivo",
    retencao_dias: "Prazo dos lances",
    base_version: "Versão base",
    "settings.CAMERA_RTSP_URL": "Endereço da câmera",
    "perfil_captura.REPLAY_BEFORE_SECONDS": "Segundos antes do clique",
    "perfil_captura.REPLAY_AFTER_SECONDS": "Segundos depois do clique",
    "perfil_captura.SEGMENT_DURATION_SECONDS": "Duração do segmento",
    "perfil_captura.KEYFRAME_INTERVAL": "Intervalo entre quadros-chave",
  };
  return (
    <div className={`${s.notice} ${s.error}`} role="alert">
      {errorText(error)}
      {error instanceof ApiError && error.violations?.length > 0 && (
        <ul>
          {error.violations.map((v, index) => (
            <li key={index}>
              {labels[v.field] || "Um campo informado"}: confira o valor permitido.
            </li>
          ))}
        </ul>
      )}
      {error instanceof ApiError && error.correlationId && (
        <p className={s.small}>
          Código para suporte: <code>{error.correlationId}</code>
        </p>
      )}
    </div>
  );
}
export function Status({ active }: { active: boolean }) {
  return (
    <span className={`${s.status} ${active ? "" : s.inactive}`}>
      {active ? "Ativa" : "Inativa"}
    </span>
  );
}
export function Pagination({
  data,
  page,
  onPage,
}: {
  data: Page<unknown> | undefined;
  page: number;
  onPage: (page: number) => void;
}) {
  if (!data || data.total <= data.size) return null;
  return (
    <nav className={s.pager} aria-label="Paginação">
      <button
        type="button"
        className="button ghost"
        disabled={!page}
        onClick={() => onPage(page - 1)}
      >
        Anterior
      </button>
      <span>
        Página {page + 1} de {Math.ceil(data.total / data.size)}
      </span>
      <button
        type="button"
        className="button ghost"
        disabled={(page + 1) * data.size >= data.total}
        onClick={() => onPage(page + 1)}
      >
        Próxima
      </button>
    </nav>
  );
}
export function ConfirmAction({
  label,
  message,
  action,
  disabled = false,
}: {
  label: string;
  message: string;
  action: () => Promise<unknown>;
  disabled?: boolean;
}) {
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  async function perform() {
    setBusy(true);
    setError(undefined);
    try {
      await action();
      setConfirm(false);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div>
      {confirm ? (
        <div className={s.confirm} role="group" aria-label={`Confirmar: ${label}`}>
          <span>{message}</span>
          <button type="button" className="button secondary" disabled={busy} onClick={perform}>
            {busy ? "Confirmando…" : "Confirmar"}
          </button>
          <button
            type="button"
            className="button ghost"
            disabled={busy}
            onClick={() => setConfirm(false)}
          >
            Cancelar
          </button>
        </div>
      ) : (
        <button
          type="button"
          className="button ghost"
          disabled={disabled}
          onClick={() => setConfirm(true)}
        >
          {label}
        </button>
      )}
      <ErrorNotice error={error} />
    </div>
  );
}
export function id(value: string) {
  return encodeURIComponent(value);
}
