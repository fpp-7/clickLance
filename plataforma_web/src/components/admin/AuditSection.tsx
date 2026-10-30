"use client";

import { useState } from "react";
import { RefreshCw } from "lucide-react";
import type { Page } from "@/lib/platform/types";
import type { AuditEntry } from "./types";
import { ErrorNotice, Pagination, id, useAdminQuery } from "./shared";
import s from "./admin.module.css";

const actions: Record<string, string> = {
  COMPANY_CREATE: "Empresa cadastrada",
  COMPANY_STATUS_CHANGE: "Estado da empresa alterado",
  COURT_CREATE: "Quadra cadastrada",
  COURT_STATUS_CHANGE: "Estado da quadra alterado",
  DEVICE_CREATE: "Dispositivo cadastrado",
  DEVICE_STATUS_CHANGE: "Estado do dispositivo alterado",
  DEVICE_TOKEN_ROTATE: "Credencial trocada",
  CONFIG_PUBLISH: "Configuração publicada",
  REPLAY_BLOCK: "Distribuição de lance alterada",
  PURGE_RESERVE: "Limpeza reservada",
  PURGE_CLAIM: "Limpeza iniciada",
  PURGE_COMPLETE: "Limpeza confirmada",
  PURGE_RETRY: "Limpeza aguardando nova tentativa",
  RECONCILIATION_FIND: "Divergência encontrada",
  ADMIN_LOGIN: "Entrada na administração",
  ADMIN_LOGOUT: "Sessão encerrada",
  COMPANY_READ: "Empresa consultada",
  COURT_READ: "Quadra consultada",
  DEVICE_READ: "Dispositivo consultado",
};

export function AuditSection({ companyId }: { companyId: string }) {
  const [page, setPage] = useState(0);
  const audit = useAdminQuery<Page<AuditEntry>>(
    `/admin/empresas/${id(companyId)}/auditoria?page=${page}&size=20`,
  );
  return (
    <section className={s.section}>
      <div className={`${s.row} ${s.sectionHeader}`}>
        <div>
          <h2>Histórico de operações</h2>
          <p className={s.muted}>
            Ações administrativas desta empresa, com resultado confirmado pelo servidor.
          </p>
        </div>
        <button
          className="button secondary"
          disabled={audit.isFetching}
          onClick={() => audit.refetch()}
        >
          <RefreshCw size={15} />
          Atualizar
        </button>
      </div>
      <ErrorNotice error={audit.error} />
      {audit.isLoading ? (
        <p role="status">Carregando histórico…</p>
      ) : audit.data?.items.length ? (
        <div className={s.tableWrap}>
          <table className={s.table}>
            <thead>
              <tr>
                <th>Data (seu fuso)</th>
                <th>Operação</th>
                <th>Resultado</th>
                <th>Referência</th>
              </tr>
            </thead>
            <tbody>
              {audit.data.items.map((entry) => (
                <tr key={entry.audit_id}>
                  <td>{new Date(entry.occurred_at).toLocaleString("pt-BR")}</td>
                  <td>
                    {actions[entry.action] || "Operação administrativa"}
                    <p className={s.small}>{entry.resource_id || "Recurso não identificado"}</p>
                  </td>
                  <td>
                    <span
                      className={`${s.status} ${entry.outcome === "SUCCESS" ? "" : s.inactive}`}
                    >
                      {entry.outcome === "SUCCESS"
                        ? "Concluída"
                        : entry.outcome === "REFUSED"
                          ? "Recusada"
                          : "Falhou"}
                    </span>
                  </td>
                  <td>
                    <details>
                      <summary className={s.small}>Código para suporte</summary>
                      <p className={s.small} style={{ overflowWrap: "anywhere" }}>
                        {entry.correlation_id}
                      </p>
                    </details>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        !audit.error && (
          <div className={s.empty}>
            <h3>Nenhuma operação registrada</h3>
            <p>As próximas ações administrativas desta empresa aparecerão aqui.</p>
          </div>
        )
      )}
      <Pagination data={audit.data} page={page} onPage={setPage} />
    </section>
  );
}
