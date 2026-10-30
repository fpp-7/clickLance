"use client";

import { useState, type FormEvent } from "react";
import { Plus, MapPin } from "lucide-react";
import { request } from "@/lib/platform/api";
import type { Page } from "@/lib/platform/types";
import type { AdminCourt } from "./types";
import { ConfirmAction, ErrorNotice, Pagination, Status, id, useAdminQuery } from "./shared";
import { DeviceSection } from "./DeviceSection";
import s from "./admin.module.css";

export function CourtSection({ companyId }: { companyId: string }) {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const [create, setCreate] = useState(false);
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const path = `/admin/empresas/${id(companyId)}/quadras`;
  const courts = useAdminQuery<Page<AdminCourt>>(`${path}?page=${page}&size=20`);
  const chosen = courts.data?.items.find((court) => court.quadra_id === selected);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setError(undefined);
    try {
      const created = await request<AdminCourt>(path, {
        role: "admin",
        method: "POST",
        body: {
          nome: String(data.get("nome")),
          active: true,
          timezone: String(data.get("timezone")),
          perfil_captura: {
            REPLAY_BEFORE_SECONDS: Number(data.get("before")),
            REPLAY_AFTER_SECONDS: Number(data.get("after")),
            SEGMENT_DURATION_SECONDS: Number(data.get("segment")),
            KEYFRAME_INTERVAL: Number(data.get("keyframe")),
          },
        },
      });
      form.reset();
      setCreate(false);
      setSelected(created.quadra_id);
      setPage(0);
      await courts.refetch();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function status(court: AdminCourt) {
    await request(`${path}/${id(court.quadra_id)}/status`, {
      role: "admin",
      method: "PATCH",
      body: { active: !court.active },
    });
    await courts.refetch();
  }
  return (
    <div className={s.workspace}>
      <div className={s.row}>
        <div>
          <h2>Quadras</h2>
          <p className={s.muted}>Cada quadra mantém seu fuso e sua janela de replay.</p>
        </div>
        <button className="button secondary" type="button" onClick={() => setCreate(!create)}>
          <Plus size={16} />
          Nova quadra
        </button>
      </div>
      {create && (
        <section className={s.section}>
          <h2>Cadastrar quadra</h2>
          <form onSubmit={submit} className={s.formGrid}>
            <label>
              Nome da quadra
              <input name="nome" required maxLength={120} placeholder="Ex.: Quadra society" />
            </label>
            <label>
              Fuso horário
              <input name="timezone" required maxLength={64} defaultValue="America/Sao_Paulo" />
              <span className={s.small}>
                Use um fuso IANA. Ele define o horário exibido aos jogadores.
              </span>
            </label>
            <label>
              Segundos antes do clique
              <input name="before" type="number" min={1} max={600} defaultValue={120} required />
            </label>
            <label>
              Segundos depois do clique
              <input name="after" type="number" min={0} max={60} defaultValue={0} required />
            </label>
            <label>
              Duração do segmento (segundos)
              <input name="segment" type="number" min={1} max={120} defaultValue={60} required />
            </label>
            <label>
              Intervalo entre quadros-chave
              <input name="keyframe" type="number" min={1} max={600} defaultValue={30} required />
            </label>
            <div className={`${s.actions} ${s.full}`}>
              <button className="button" disabled={busy}>
                {busy ? "Cadastrando…" : "Cadastrar quadra"}
              </button>
              <button className="button ghost" type="button" onClick={() => setCreate(false)}>
                Cancelar
              </button>
            </div>
          </form>
          <ErrorNotice error={error} />
        </section>
      )}
      <ErrorNotice error={courts.error} />
      {courts.isLoading ? (
        <p role="status">Carregando quadras…</p>
      ) : courts.data?.items.length ? (
        <>
          <div className={s.split}>
            <section className={s.list} aria-label="Quadras da empresa">
              {courts.data.items.map((court) => (
                <div className={s.section} key={court.quadra_id}>
                  <button
                    className={s.listButton}
                    aria-pressed={selected === court.quadra_id}
                    type="button"
                    onClick={() => setSelected(court.quadra_id)}
                  >
                    <span>
                      <strong>{court.nome}</strong>
                      <small>{court.timezone.replaceAll("_", " ")}</small>
                    </span>
                    <MapPin size={19} />
                  </button>
                  <div className={s.row} style={{ marginTop: 12 }}>
                    <Status active={court.active} />
                    <ConfirmAction
                      label={court.active ? "Inativar" : "Ativar"}
                      message={
                        court.active
                          ? `Interromper configuração e envio em ${court.nome}?`
                          : `Reativar ${court.nome}?`
                      }
                      action={() => status(court)}
                    />
                  </div>
                </div>
              ))}
            </section>
            <section>
              {chosen ? (
                <DeviceSection key={chosen.quadra_id} companyId={companyId} court={chosen} />
              ) : (
                <div className={`${s.section} ${s.empty}`}>
                  <MapPin size={25} />
                  <h3 style={{ marginTop: 18 }}>Escolha uma quadra</h3>
                  <p>Consulte os dispositivos, a captura e o último contato da instalação.</p>
                </div>
              )}
            </section>
          </div>
          <Pagination
            data={courts.data}
            page={page}
            onPage={(n) => {
              setPage(n);
              setSelected(null);
            }}
          />
        </>
      ) : (
        !courts.error && (
          <section className={`${s.section} ${s.empty}`}>
            <h3>A primeira quadra começa aqui</h3>
            <p>Cadastre a quadra para vincular uma instalação de captura.</p>
          </section>
        )
      )}
    </div>
  );
}
