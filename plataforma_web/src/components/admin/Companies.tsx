"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { Building2, Plus } from "lucide-react";
import { useSession } from "@/lib/platform/session";
import { request } from "@/lib/platform/api";
import type { Page } from "@/lib/platform/types";
import type { Company } from "./types";
import { ErrorNotice, Pagination, Status, id, useAdminQuery } from "./shared";
import s from "./admin.module.css";

export function Companies() {
  const { user } = useSession("admin");
  const [page, setPage] = useState(0);
  const [create, setCreate] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const companies = useAdminQuery<Page<Company>>(`/admin/empresas?page=${page}&size=20`);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setError(undefined);
    setBusy(true);
    try {
      await request("/admin/empresas", {
        role: "admin",
        method: "POST",
        body: {
          nome: String(data.get("nome")),
          active: true,
          retencao_dias: Number(data.get("retencao_dias")),
        },
      });
      form.reset();
      setCreate(false);
      await companies.refetch();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className={s.workspace}>
      <header className={s.row}>
        <div>
          <p className={s.muted}>Seu espaço de operação</p>
          <h1>Empresas e quadras</h1>
          <p className={s.muted}>Escolha uma empresa para acompanhar suas instalações.</p>
        </div>
        {user?.platform_admin && (
          <button type="button" className="button" onClick={() => setCreate(!create)}>
            <Plus size={17} />
            Nova empresa
          </button>
        )}
      </header>
      {create && user?.platform_admin && (
        <section className={s.section}>
          <h2>Cadastrar empresa</h2>
          <form onSubmit={submit} className={s.formGrid}>
            <label>
              Nome da empresa
              <input name="nome" required maxLength={120} placeholder="Nome da arena" />
            </label>
            <label>
              Disponibilidade inicial dos lances (dias)
              <input
                name="retencao_dias"
                type="number"
                min={1}
                max={30}
                defaultValue={2}
                required
              />
              <span className={s.small}>
                De 1 a 30 dias. A biblioteca preserva os lances salvos.
              </span>
            </label>
            <div className={`${s.actions} ${s.full}`}>
              <button className="button" disabled={busy}>
                {busy ? "Cadastrando…" : "Cadastrar empresa"}
              </button>
              <button type="button" className="button ghost" onClick={() => setCreate(false)}>
                Cancelar
              </button>
            </div>
          </form>
          <ErrorNotice error={error} />
        </section>
      )}
      <section className={s.section}>
        <div className={`${s.row} ${s.sectionHeader}`}>
          <h2>Empresas autorizadas</h2>
          <Building2 size={23} color="#657575" />
        </div>
        <ErrorNotice error={companies.error} />
        {companies.isLoading ? (
          <p className={s.empty} role="status">
            Carregando suas empresas…
          </p>
        ) : companies.data?.items.length ? (
          <div className={s.tableWrap}>
            <table className={s.table}>
              <thead>
                <tr>
                  <th>Empresa</th>
                  <th>Estado</th>
                  <th>Disponibilidade dos lances</th>
                  <th>
                    <span className="visually-hidden">Abrir empresa</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {companies.data.items.map((company) => (
                  <tr key={company.empresa_id}>
                    <td>
                      <Link href={`/admin/empresas/${id(company.empresa_id)}`}>{company.nome}</Link>
                    </td>
                    <td>
                      <Status active={company.active} />
                    </td>
                    <td>
                      {company.retencao_dias} {company.retencao_dias === 1 ? "dia" : "dias"}
                    </td>
                    <td>
                      <Link href={`/admin/empresas/${id(company.empresa_id)}`}>Gerenciar</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          !companies.error && (
            <div className={s.empty}>
              <h3>Nenhuma empresa disponível</h3>
              <p>
                {user?.platform_admin
                  ? "Cadastre a primeira empresa para começar a configurar as quadras."
                  : "Peça ao responsável da plataforma para conferir seus vínculos de acesso."}
              </p>
            </div>
          )
        )}
        <Pagination data={companies.data} page={page} onPage={setPage} />
        {companies.error && (
          <button className="button secondary" onClick={() => companies.refetch()}>
            Tentar novamente
          </button>
        )}
      </section>
    </div>
  );
}
