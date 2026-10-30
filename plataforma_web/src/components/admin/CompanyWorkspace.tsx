"use client";

import Link from "next/link";
import { useState } from "react";
import { ChevronLeft } from "lucide-react";
import { useSession } from "@/lib/platform/session";
import { request } from "@/lib/platform/api";
import type { Company } from "./types";
import { ConfirmAction, ErrorNotice, Status, id, useAdminQuery } from "./shared";
import { CourtSection } from "./CourtSection";
import { AuditSection } from "./AuditSection";
import { OperationSection } from "./OperationSection";
import s from "./admin.module.css";

export function CompanyWorkspace({ companyId }: { companyId: string }) {
  const { user } = useSession("admin");
  const company = useAdminQuery<Company>(`/admin/empresas/${id(companyId)}`);
  const [tab, setTab] = useState("quadras");
  async function status() {
    await request(`/admin/empresas/${id(companyId)}/status`, {
      role: "admin",
      method: "PATCH",
      body: { active: !company.data?.active },
    });
    await company.refetch();
  }
  return (
    <div className={s.workspace}>
      <Link href="/admin" className={`text-link ${s.actions}`}>
        <ChevronLeft size={16} />
        Empresas
      </Link>
      <ErrorNotice error={company.error} />
      {company.isLoading ? (
        <p role="status">Carregando empresa…</p>
      ) : (
        company.data && (
          <>
            <header className={s.row}>
              <div>
                <p className={s.muted}>Operação da arena</p>
                <h1>{company.data.nome}</h1>
                <div className={s.actions}>
                  <Status active={company.data.active} />
                  <span className={s.muted}>
                    Lances disponíveis por {company.data.retencao_dias} dias
                  </span>
                </div>
              </div>
              {user?.platform_admin && (
                <ConfirmAction
                  label={company.data.active ? "Inativar empresa" : "Ativar empresa"}
                  message={
                    company.data.active
                      ? "A configuração e o envio de lances serão interrompidos em todas as quadras desta empresa."
                      : "Permitir novamente configuração e envio nas origens ativas desta empresa?"
                  }
                  action={status}
                />
              )}
            </header>
            <nav className={s.tabs} role="tablist" aria-label="Gerenciar empresa">
              {[
                { id: "quadras", label: "Quadras e dispositivos" },
                { id: "auditoria", label: "Histórico de operações" },
                { id: "operacao", label: "Conteúdo e manutenção" },
              ].map((item) => (
                <button
                  type="button"
                  key={item.id}
                  id={`tab-${item.id}`}
                  role="tab"
                  aria-controls={`panel-${item.id}`}
                  aria-selected={tab === item.id}
                  onClick={() => setTab(item.id)}
                >
                  {item.label}
                </button>
              ))}
            </nav>
            <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
              {tab === "quadras" ? (
                <CourtSection companyId={companyId} />
              ) : tab === "auditoria" ? (
                <AuditSection companyId={companyId} />
              ) : (
                <OperationSection companyId={companyId} />
              )}
            </div>
          </>
        )
      )}
    </div>
  );
}
