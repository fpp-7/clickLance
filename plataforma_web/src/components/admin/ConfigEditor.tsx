"use client";

import { useState, type FormEvent } from "react";
import { request } from "@/lib/platform/api";
import { ErrorNotice, id } from "./shared";
import s from "./admin.module.css";

export function ConfigEditor({ deviceId }: { deviceId: string }) {
  const [source, setSource] = useState("rtsp");
  const [version, setVersion] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const [published, setPublished] = useState(false);
  async function publish(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const settings: Record<string, unknown> = { CAMERA_SOURCE: source };
    if (source === "rtsp") {
      settings.CAMERA_RTSP_URL = String(data.get("camera_url"));
      settings.CAMERA_RTSP_TRANSPORT = String(data.get("transport"));
    } else {
      settings.CAMERA_DEVICE_NAME = String(data.get("camera_name"));
    }
    setBusy(true);
    setError(undefined);
    setPublished(false);
    try {
      const result = await request<{ version: string }>(
        `/admin/dispositivos/${id(deviceId)}/configuracoes`,
        { role: "admin", method: "POST", body: { base_version: version.trim() || null, settings } },
      );
      setVersion(result.version);
      setPublished(true);
      const camera = form.elements.namedItem("camera_url");
      if (camera instanceof HTMLInputElement) camera.value = "";
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className={s.section}>
      <details className={s.details}>
        <summary>Publicar configuração de captura</summary>
        <p className={s.muted} style={{ marginBottom: 20 }}>
          A configuração entra no próximo ciclo de atualização da instalação. Os lances já aceitos
          mantêm seus dados originais.
        </p>
        <form className={s.formGrid} onSubmit={publish}>
          <label className={s.full}>
            Versão base
            <input
              name="version"
              value={version}
              onChange={(event) => setVersion(event.target.value)}
              maxLength={64}
              placeholder="Vazio somente na primeira publicação"
              autoComplete="off"
            />
            <span className={s.small}>
              Em uma instalação existente, informe a versão atual registrada pelo operador. Uma
              versão divergente será recusada.
            </span>
          </label>
          <label>
            Fonte da câmera
            <select value={source} onChange={(event) => setSource(event.target.value)}>
              <option value="rtsp">Câmera IP (RTSP)</option>
              <option value="dshow">Câmera local Windows</option>
            </select>
          </label>
          {source === "rtsp" ? (
            <>
              <label>
                Transporte
                <select name="transport" defaultValue="tcp">
                  <option value="tcp">TCP</option>
                  <option value="udp">UDP</option>
                </select>
              </label>
              <label className={s.full}>
                Endereço RTSP da câmera
                <input
                  name="camera_url"
                  type="password"
                  required
                  maxLength={2048}
                  autoComplete="new-password"
                  placeholder="rtsp://câmera/stream"
                />
                <span className={s.small}>
                  O endereço pode conter credenciais. Ele não será exibido na consulta geral.
                </span>
              </label>
            </>
          ) : (
            <label>
              Nome da câmera no Windows
              <input
                name="camera_name"
                required
                maxLength={128}
                placeholder="Nome do dispositivo"
              />
            </label>
          )}
          <div className={s.full}>
            <button className="button secondary" disabled={busy}>
              {busy ? "Publicando…" : "Publicar configuração"}
            </button>
          </div>
        </form>
        <ErrorNotice error={error} />
        {published && (
          <p className={s.notice} role="status" style={{ marginTop: 20 }}>
            Configuração publicada. A versão base foi atualizada para a próxima publicação.
          </p>
        )}
      </details>
    </section>
  );
}
