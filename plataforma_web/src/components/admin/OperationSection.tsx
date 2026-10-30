"use client";

import { useState, type FormEvent } from "react";
import { ShieldCheck } from "lucide-react";
import { request } from "@/lib/platform/api";
import { ConfirmAction, ErrorNotice, id, useAdminQuery } from "./shared";
import s from "./admin.module.css";

interface Issue {
  id: string;
  kind: string;
  first_seen_at: string;
  last_seen_at: string;
}

export function OperationSection({ companyId }: { companyId: string }) {
  const [error, setError] = useState<unknown>();
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [limit, setLimit] = useState(20);
  const [pendingBlock, setPendingBlock] = useState<{
    event: string;
    blocked: boolean;
    reason: string;
  } | null>(null);
  const issues = useAdminQuery<Issue[]>(`/admin/empresas/${id(companyId)}/reconciliacao`);
  function prepareBlock(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPendingBlock({
      event: String(data.get("event")),
      blocked: data.get("blocked") === "true",
      reason: String(data.get("reason")),
    });
    setMessage("");
    setError(undefined);
  }
  async function block() {
    if (!pendingBlock) return;
    setBusy(true);
    setError(undefined);
    try {
      await request(`/admin/lances/${id(pendingBlock.event)}/bloqueio`, {
        role: "admin",
        method: "PATCH",
        body: { blocked: pendingBlock.blocked, reason: pendingBlock.reason },
      });
      setMessage(
        pendingBlock.blocked
          ? "Distribuição bloqueada para todos os jogadores, inclusive nas bibliotecas."
          : "Bloqueio removido. O lance volta a seguir as regras de disponibilidade e conservação.",
      );
      setPendingBlock(null);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function maintenance(action: "reservas" | "executar" | "reconciliar") {
    setError(undefined);
    setMessage("");
    const url =
      action === "reconciliar"
        ? `/admin/empresas/${id(companyId)}/reconciliacao`
        : `/admin/empresas/${id(companyId)}/purga/${action}`;
    const result = await request<Record<string, number>>(url, {
      role: "admin",
      method: "POST",
      body: action === "reconciliar" ? {} : { limit },
    });
    if (action === "reservas")
      setMessage(`${result.reserved ?? 0} lances elegíveis reservados para limpeza.`);
    if (action === "executar")
      setMessage(
        `${result.completed ?? 0} exclusões confirmadas. ${result.failed ?? 0} trabalhos permanecem aguardando nova tentativa.`,
      );
    if (action === "reconciliar") {
      setMessage(
        `Verificação concluída: ${result.orphan_objects ?? 0} objetos sem registro, ${result.missing_objects ?? 0} ausências e ${result.purged_objects ?? 0} objetos reaparecidos.`,
      );
      await issues.refetch();
    }
  }
  const kinds: Record<string, string> = {
    ORPHAN_OBJECT: "Objeto sem registro",
    MISSING_OBJECT: "Arquivo não encontrado",
    PURGED_OBJECT_PRESENT: "Arquivo reaparecido após exclusão",
  };
  return (
    <div className={s.workspace}>
      <section className={s.section}>
        <div className={`${s.actions} ${s.sectionHeader}`}>
          <ShieldCheck size={22} color="#087d66" />
          <div>
            <h2>Distribuição de conteúdo</h2>
            <p className={s.muted}>
              Retire um lance de todas as áreas sem declarar o arquivo excluído.
            </p>
          </div>
        </div>
        <form className={s.formGrid} onSubmit={prepareBlock}>
          <label className={s.full}>
            Identificador do lance
            <input
              name="event"
              required
              pattern="[a-f0-9]{32}"
              minLength={32}
              maxLength={32}
              placeholder="Identificador de 32 caracteres fornecido ao suporte"
              autoComplete="off"
            />
          </label>
          <label>
            Operação
            <select name="blocked">
              <option value="true">Bloquear distribuição</option>
              <option value="false">Remover bloqueio</option>
            </select>
          </label>
          <label>
            Motivo
            <select name="reason">
              <option value="PRIVACY">Solicitação de privacidade</option>
              <option value="POLICY">Política de conteúdo</option>
              <option value="OPERATIONAL">Ocorrência operacional</option>
            </select>
          </label>
          <div className={s.full}>
            <button className="button secondary" disabled={busy}>
              Revisar alteração
            </button>
          </div>
        </form>
        {pendingBlock && (
          <div className={s.confirm} style={{ marginTop: 20 }}>
            <span>
              {pendingBlock.blocked
                ? "Bloquear este lance também para quem já o salvou?"
                : "Remover o bloqueio deste lance?"}
            </span>
            <button className="button secondary" disabled={busy} onClick={block}>
              {busy ? "Confirmando…" : "Confirmar alteração"}
            </button>
            <button className="button ghost" disabled={busy} onClick={() => setPendingBlock(null)}>
              Cancelar
            </button>
          </div>
        )}
        <p className={s.small} style={{ marginTop: 20 }}>
          Links emitidos anteriormente podem funcionar por até cinco minutos. Vídeos já baixados não
          são recolhidos.
        </p>
        <ErrorNotice error={error} />
        {message && (
          <p className={s.notice} role="status" style={{ marginTop: 18 }}>
            {message}
          </p>
        )}
      </section>
      <section className={s.section}>
        <details className={s.details}>
          <summary>Manutenção de arquivos</summary>
          <p className={s.muted} style={{ marginBottom: 20 }}>
            A limpeza exige configuração prévia do storage pelo operador. Somente lances elegíveis,
            expirados e sem vínculo de conservação podem ser reservados. Arquivos sem registro são
            apenas reportados.
          </p>
          <label style={{ maxWidth: 220, marginBottom: 20 }}>
            Máximo de trabalhos por execução
            <input
              type="number"
              min={1}
              max={100}
              required
              value={limit}
              onChange={(event) => setLimit(Number(event.target.value))}
            />
          </label>
          <div className={s.list}>
            <ConfirmAction
              label="Reservar lances elegíveis"
              message="Reservar lances expirados sem biblioteca? A reserva impede novos salvamentos."
              disabled={!Number.isInteger(limit) || limit < 1 || limit > 100}
              action={() => maintenance("reservas")}
            />
            <ConfirmAction
              label="Executar exclusões reservadas"
              message="Excluir do storage os arquivos já reservados? Esta operação não pode ser desfeita pelo backend."
              disabled={!Number.isInteger(limit) || limit < 1 || limit > 100}
              action={() => maintenance("executar")}
            />
            <ConfirmAction
              label="Verificar divergências"
              message="Consultar os objetos autorizados desta empresa e registrar divergências sem excluir arquivos?"
              action={() => maintenance("reconciliar")}
            />
          </div>
        </details>
      </section>
      <section className={s.section}>
        <h2>Divergências registradas</h2>
        <ErrorNotice error={issues.error} />
        {issues.isLoading ? (
          <p role="status">Consultando divergências…</p>
        ) : issues.data?.length ? (
          <div className={s.tableWrap}>
            <table className={s.table}>
              <thead>
                <tr>
                  <th>Ocorrência</th>
                  <th>Primeira observação</th>
                  <th>Última observação</th>
                </tr>
              </thead>
              <tbody>
                {issues.data.map((issue) => (
                  <tr key={issue.id}>
                    <td>{kinds[issue.kind] || "Divergência operacional"}</td>
                    <td>{new Date(issue.first_seen_at).toLocaleString("pt-BR")}</td>
                    <td>{new Date(issue.last_seen_at).toLocaleString("pt-BR")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          !issues.error && (
            <p className={s.empty}>
              Nenhuma divergência registrada. Isso não substitui uma verificação do storage.
            </p>
          )
        )}
        <button
          className="button ghost"
          disabled={issues.isFetching}
          onClick={() => issues.refetch()}
        >
          Atualizar ocorrências
        </button>
      </section>
    </div>
  );
}
