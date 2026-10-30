"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bookmark, BookmarkCheck, Download, Info, Play, RefreshCw } from "lucide-react";
import { useRef, useState } from "react";
import { isDemo, platform, safeMediaUrl } from "@/lib/platform/api";
import { useSession } from "@/lib/platform/session";
import {
  Back,
  errorMessage,
  Failure,
  Loading,
  localDateTime,
  PitchArtwork,
  PlayerGate,
} from "./shared";
import styles from "./Player.module.css";

function approvedMediaUrl(value: string) {
  const url = safeMediaUrl(value);
  if (url) return url;
  throw new Error("O endereço deste vídeo não está disponível. Solicite um novo acesso.");
}

export function ReplayView(props: { id: string; zone?: string }) {
  const { user } = useSession("player");
  return <ReplayContent key={`${user?.usuario_id || "anonymous"}:${props.id}`} {...props} />;
}

function ReplayContent({ id, zone = "America/Sao_Paulo" }: { id: string; zone?: string }) {
  const { user } = useSession("player");
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: ["player", "replay", user?.usuario_id, id],
    queryFn: () => platform.replay(id),
    enabled: !!user,
  });
  const [source, setSource] = useState<string>();
  const [mediaError, setMediaError] = useState<string>();
  const [feedback, setFeedback] = useState<string>();
  const videoRef = useRef<HTMLVideoElement>(null);
  const grant = useMutation({
    mutationFn: async () => approvedMediaUrl((await platform.access(id, false)).url),
    onSuccess: (url) => {
      setMediaError(undefined);
      setSource(url);
    },
    onError: (error) => setMediaError(errorMessage(error)),
  });
  const save = useMutation({
    mutationFn: async () => {
      if (query.data?.salvo) await platform.remove(id);
      else await platform.save(id);
    },
    onSuccess: async () => {
      setFeedback(
        query.data?.salvo ? "Lance removido da sua biblioteca." : "Lance salvo na sua biblioteca.",
      );
      await queryClient.invalidateQueries({ queryKey: ["player"] });
    },
    onError: () => setFeedback(undefined),
  });
  const download = useMutation({
    mutationFn: async () => {
      const access = await platform.access(id, true);
      const anchor = document.createElement("a");
      anchor.href = approvedMediaUrl(access.url);
      anchor.download = "meu-lance.mp4";
      anchor.target = "_blank";
      anchor.rel = "noopener noreferrer";
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
    },
    onSuccess: () => setFeedback("Seu download foi solicitado."),
    onError: () => setFeedback(undefined),
  });
  const replay = query.isError ? undefined : query.data;
  return (
    <PlayerGate>
      <div className={styles.page}>
        <Back href={replay ? `/quadras/${encodeURIComponent(replay.quadra_id)}` : "/arenas"}>
          Voltar para a quadra
        </Back>
        {query.isPending && <Loading label="Preparando seu lance…" />}
        {query.isError && <Failure error={query.error} retry={() => void query.refetch()} />}
        {replay && (
          <>
            <header className={styles.heading}>
              <div>
                <h1 className={styles.title}>Vale o replay.</h1>
                <p className={styles.intro}>
                  {localDateTime(replay.timestamp, zone, {
                    day: "numeric",
                    month: "long",
                    year: "numeric",
                  })}
                  , às{" "}
                  {localDateTime(replay.timestamp, zone, {
                    hour: "2-digit",
                    minute: "2-digit",
                    second: "2-digit",
                  })}
                </p>
              </div>
            </header>
            <div className={styles.playerLayout}>
              <section aria-label="Reprodução do lance">
                <div className={styles.playerStage}>
                  {source ? (
                    <video
                      key={source}
                      ref={videoRef}
                      src={source}
                      controls
                      playsInline
                      autoPlay
                      preload="metadata"
                      onError={() =>
                        setMediaError(
                          "Não foi possível reproduzir. O acesso pode ter expirado; solicite um novo vídeo abaixo.",
                        )
                      }
                      aria-label="Vídeo do seu lance"
                    />
                  ) : (
                    <>
                      <PitchArtwork />
                      <div className={styles.playPrompt}>
                        <button
                          className={styles.playMain}
                          disabled={grant.isPending}
                          onClick={() => grant.mutate()}
                          aria-label="Reproduzir lance"
                        >
                          {grant.isPending ? (
                            <RefreshCw className={styles.spinner} />
                          ) : (
                            <Play aria-hidden="true" />
                          )}
                        </button>
                        <p>
                          {grant.isPending
                            ? "Preparando o vídeo…"
                            : isDemo
                              ? "Vídeo de exemplo. As imagens não são gravações desta quadra."
                              : "Seu lance está pronto para assistir."}
                        </p>
                      </div>
                    </>
                  )}
                </div>
                {mediaError && (
                  <div className="notice error" role="alert" style={{ marginTop: 16 }}>
                    {mediaError}
                    <button
                      className="text-link"
                      onClick={() => grant.mutate()}
                      disabled={grant.isPending}
                    >
                      {grant.isPending ? "Renovando…" : "Renovar acesso ao vídeo"}
                    </button>
                  </div>
                )}
                <div className={styles.playerBar}>
                  <p>{isDemo ? "Reprodução demonstrativa" : "Vídeo com acesso protegido"}</p>
                  {source && (
                    <label className={styles.fieldLabel}>
                      Velocidade{" "}
                      <select
                        className={styles.date}
                        defaultValue="1"
                        onChange={(event) => {
                          if (videoRef.current)
                            videoRef.current.playbackRate = Number(event.target.value);
                        }}
                        aria-label="Velocidade de reprodução"
                      >
                        <option value="0.5">0,5×</option>
                        <option value="0.75">0,75×</option>
                        <option value="1">Normal</option>
                        <option value="1.5">1,5×</option>
                        <option value="2">2×</option>
                      </select>
                    </label>
                  )}
                </div>
                <div className={styles.playerBar}>
                  <div className={styles.playerActions}>
                    <button
                      className={`button ${replay.salvo ? "secondary" : ""}`}
                      onClick={() => {
                        setFeedback(undefined);
                        save.mutate();
                      }}
                      disabled={save.isPending}
                    >
                      {replay.salvo ? (
                        <BookmarkCheck size={17} aria-hidden="true" />
                      ) : (
                        <Bookmark size={17} aria-hidden="true" />
                      )}
                      {save.isPending
                        ? "Atualizando…"
                        : replay.salvo
                          ? "Remover da biblioteca"
                          : "Salvar na biblioteca"}
                    </button>
                    <button
                      className="button secondary"
                      onClick={() => {
                        setFeedback(undefined);
                        download.mutate();
                      }}
                      disabled={download.isPending}
                    >
                      <Download size={17} aria-hidden="true" />
                      {download.isPending ? "Preparando…" : "Baixar vídeo"}
                    </button>
                  </div>
                </div>
                {feedback && (
                  <p className="notice success" role="status" style={{ marginTop: 16 }}>
                    {feedback}
                  </p>
                )}
                {(save.isError || download.isError) && (
                  <p className="notice error" role="alert" style={{ marginTop: 16 }}>
                    {errorMessage(save.error || download.error)}
                  </p>
                )}
              </section>
              <aside className={styles.sideInfo}>
                <h2>Este lance é seu para rever</h2>
                <dl>
                  <div className={styles.detailItem}>
                    <dt>Quando aconteceu</dt>
                    <dd>
                      {localDateTime(replay.timestamp, zone, {
                        dateStyle: "short",
                        timeStyle: "short",
                      })}
                    </dd>
                  </div>
                  <div className={styles.detailItem}>
                    <dt>Na sua biblioteca</dt>
                    <dd>{replay.salvo ? "Salvo para você" : "Ainda não salvo"}</dd>
                  </div>
                  <div className={styles.detailItem}>
                    <dt>Disponível na quadra até</dt>
                    <dd>
                      {localDateTime(replay.expira_em, zone, {
                        dateStyle: "short",
                        timeStyle: "short",
                      })}
                    </dd>
                  </div>
                </dl>
                <p className={styles.sideNote}>
                  <Info
                    size={15}
                    aria-hidden="true"
                    style={{ verticalAlign: "middle", marginRight: 5 }}
                  />
                  Salve antes de expirar para rever pela biblioteca. Você pode guardar até 100
                  lances enquanto sua conta estiver ativa. Conteúdos retirados deixam de estar
                  acessíveis.
                </p>
              </aside>
            </div>
          </>
        )}
      </div>
    </PlayerGate>
  );
}
