"use client";

import { useState, type FormEvent } from "react";
import { Copy, EyeOff, Plus, RefreshCw, Radio } from "lucide-react";
import { request } from "@/lib/platform/api";
import type { Page } from "@/lib/platform/types";
import type { AdminCourt, Device, Health, IssuedDevice } from "./types";
import { ConfirmAction, ErrorNotice, Pagination, Status, id, useAdminQuery } from "./shared";
import { ConfigEditor } from "./ConfigEditor";
import s from "./admin.module.css";

export function DeviceSection({ companyId, court }: { companyId: string; court: AdminCourt }) {
  const [page, setPage] = useState(0);
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [issued, setIssued] = useState<IssuedDevice | null>(null);
  const [copy, setCopy] = useState("");
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const path = `/admin/empresas/${id(companyId)}/quadras/${id(court.quadra_id)}/dispositivos`;
  const devices = useAdminQuery<Page<Device>>(`${path}?page=${page}&size=20`);
  const chosen = devices.data?.items.find((device) => device.dispositivo_id === selected);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setIssued(null);
    setError(undefined);
    try {
      const result = await request<IssuedDevice>(path, {
        role: "admin",
        method: "POST",
        body: {
          dispositivo_id: String(data.get("id")),
          active: true,
          bucket: String(data.get("bucket")),
        },
      });
      setIssued(result);
      setCopy("");
      setSelected(result.dispositivo.dispositivo_id);
      setCreate(false);
      form.reset();
      await devices.refetch();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function toggle(device: Device) {
    await request(`/admin/dispositivos/${id(device.dispositivo_id)}/status`, {
      role: "admin",
      method: "PATCH",
      body: { active: !device.active },
    });
    await devices.refetch();
  }
  async function rotate(device: Device) {
    setIssued(null);
    const result = await request<IssuedDevice>(
      `/admin/dispositivos/${id(device.dispositivo_id)}/credenciais/rotacao`,
      { role: "admin", method: "POST", body: {} },
    );
    setIssued(result);
    setCopy("");
  }
  async function copySecret() {
    if (!issued) return;
    try {
      await navigator.clipboard.writeText(issued.device_token);
      setCopy("Credencial copiada.");
    } catch {
      setCopy("Não foi possível copiar. Selecione a credencial e copie manualmente.");
    }
  }
  return (
    <div className={s.workspace}>
      <section className={s.section}>
        <div className={`${s.row} ${s.sectionHeader}`}>
          <div>
            <h2>{court.nome}</h2>
            <p className={s.muted}>Dispositivos de captura</p>
          </div>
          <button className="button secondary" type="button" onClick={() => setCreate(!create)}>
            <Plus size={16} />
            Dispositivo
          </button>
        </div>
        {create && (
          <form className={s.formGrid} onSubmit={submit}>
            <label>
              Identificador do dispositivo
              <input
                name="id"
                required
                maxLength={32}
                pattern="[A-Za-z0-9_-]{1,32}"
                placeholder="Ex.: botao-quadra-1"
              />
              <span className={s.small}>
                Use o mesmo identificador da instalação. Ele não poderá ser transferido para outra
                quadra.
              </span>
            </label>
            <label>
              Bucket autorizado
              <input
                name="bucket"
                required
                minLength={3}
                maxLength={63}
                placeholder="Nome fornecido pela plataforma"
              />
            </label>
            <div className={`${s.actions} ${s.full}`}>
              <button className="button" disabled={busy}>
                {busy ? "Cadastrando…" : "Cadastrar dispositivo"}
              </button>
              <button type="button" className="button ghost" onClick={() => setCreate(false)}>
                Cancelar
              </button>
            </div>
          </form>
        )}
        <ErrorNotice error={error || devices.error} />
        {devices.isLoading ? (
          <p role="status">Carregando dispositivos…</p>
        ) : devices.data?.items.length ? (
          <div className={s.list}>
            {devices.data.items.map((device) => (
              <button
                key={device.dispositivo_id}
                type="button"
                className={s.listButton}
                aria-pressed={selected === device.dispositivo_id}
                onClick={() => setSelected(device.dispositivo_id)}
              >
                <span>
                  <strong>{device.dispositivo_id}</strong>
                  <small>Instalação de {court.nome}</small>
                </span>
                <Status active={device.active} />
              </button>
            ))}
          </div>
        ) : (
          !devices.error && (
            <div className={s.empty}>
              <h3>Instalação ainda não vinculada</h3>
              <p>Cadastre o dispositivo para configurar a captura desta quadra.</p>
            </div>
          )
        )}
        <Pagination
          data={devices.data}
          page={page}
          onPage={(n) => {
            setPage(n);
            setSelected(null);
            setIssued(null);
          }}
        />
      </section>
      {issued && (
        <section className={s.secret} aria-label="Credencial emitida">
          <h3>Guarde esta credencial agora</h3>
          <p className={s.small}>
            Ela pertence a {issued.dispositivo.dispositivo_id} e aparece somente nesta emissão.
            Configure-a na instalação antes de fechar esta mensagem.
          </p>
          <code>{issued.device_token}</code>
          <div className={s.actions}>
            <button type="button" className="button secondary" onClick={copySecret}>
              <Copy size={16} />
              Copiar credencial
            </button>
            <button type="button" className="button ghost" onClick={() => setIssued(null)}>
              <EyeOff size={16} />
              Já guardei
            </button>
          </div>
          <p className={s.small} role="status">
            {copy}
          </p>
        </section>
      )}
      {chosen && (
        <>
          <section className={s.section}>
            <div className={s.row}>
              <h3>{chosen.dispositivo_id}</h3>
              <Status active={chosen.active} />
            </div>
            <div className={s.actions} style={{ marginTop: 16 }}>
              <ConfirmAction
                label={chosen.active ? "Inativar dispositivo" : "Ativar dispositivo"}
                message={
                  chosen.active
                    ? "Interromper configuração e registro de lances deste dispositivo? A fila local será preservada."
                    : "Permitir configuração e registro neste dispositivo?"
                }
                action={() => toggle(chosen)}
              />
              <ConfirmAction
                label="Trocar credencial"
                message="A credencial anterior deixará de funcionar imediatamente. Será necessário atualizar a instalação."
                action={() => rotate(chosen)}
              />
            </div>
          </section>
          <DeviceHealth deviceId={chosen.dispositivo_id} />
          <ConfigEditor key={chosen.dispositivo_id} deviceId={chosen.dispositivo_id} />
        </>
      )}
    </div>
  );
}

function DeviceHealth({ deviceId }: { deviceId: string }) {
  const health = useAdminQuery<Health>(`/admin/dispositivos/${id(deviceId)}/saude`);
  const capture: Record<string, string> = {
    READY: "Pronta",
    WARMING_UP: "Preparando captura",
    UNAVAILABLE: "Indisponível",
  };
  return (
    <section className={s.section}>
      <div className={s.row}>
        <h3 className={s.actions}>
          <Radio size={18} />
          Estado da instalação
        </h3>
        <button
          type="button"
          className="button ghost"
          disabled={health.isFetching}
          onClick={() => health.refetch()}
        >
          <RefreshCw size={15} />
          Atualizar
        </button>
      </div>
      <ErrorNotice error={health.error} />
      {health.isLoading ? (
        <p role="status">Consultando último contato…</p>
      ) : (
        health.data && (
          <>
            <p className={s.muted} style={{ marginTop: 12 }}>
              {health.data.contact_state === "UNKNOWN"
                ? "Nenhum contato recebido. Configure o envio de saúde na instalação."
                : health.data.contact_state === "STALE"
                  ? "O contato está desatualizado. Confira a conexão e a captura no local."
                  : "Contato recente recebido."}
            </p>
            {health.data.health && (
              <dl className={s.detailList}>
                <div>
                  <dt>Captura informada</dt>
                  <dd>{capture[health.data.health.capture_state] || "Não informada"}</dd>
                </div>
                <div>
                  <dt>Lances pendentes</dt>
                  <dd>{health.data.health.pending_events}</dd>
                </div>
                <div>
                  <dt>Último contato (seu fuso)</dt>
                  <dd>{new Date(health.data.health.received_at).toLocaleString("pt-BR")}</dd>
                </div>
                <div>
                  <dt>Versão da instalação</dt>
                  <dd>{health.data.health.gateway_version}</dd>
                </div>
              </dl>
            )}
            <p className={s.small} style={{ marginTop: 20 }}>
              Contato recente não garante que a câmera esteja produzindo vídeo.
            </p>
          </>
        )
      )}
    </section>
  );
}
