"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bookmark } from "lucide-react";
import { useState } from "react";
import { platform } from "@/lib/platform/api";
import { useSession } from "@/lib/platform/session";
import { Empty, errorMessage, Failure, Loading, Pagination, PlayerGate } from "./shared";
import { ReplayCard } from "./ReplayCard";
import styles from "./Player.module.css";

export function LibraryView() {
  const { user } = useSession("player");
  const [page, setPage] = useState(0);
  const [feedback, setFeedback] = useState<string>();
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ["player", "library", user?.usuario_id, page],
    queryFn: () => platform.library(page, 12),
    enabled: !!user,
  });
  const remove = useMutation({
    mutationFn: (id: string) => platform.remove(id),
    onSuccess: async () => {
      setFeedback("Lance removido da sua biblioteca.");
      if (page > 0 && query.data?.items.length === 1) setPage(page - 1);
      await client.invalidateQueries({ queryKey: ["player"] });
    },
    onError: () => setFeedback(undefined),
  });
  return (
    <PlayerGate>
      <div className={styles.page}>
        <header className={styles.heading}>
          <div>
            <h1 className={styles.title}>Os seus melhores.</h1>
            <p className={styles.intro}>Os lances que você decidiu guardar. Prontos para rever.</p>
          </div>
          <div className={styles.quotaLabel}>
            <span>{query.data?.total || 0} lances disponíveis</span>
            <p>Limite de 100 salvos</p>
          </div>
        </header>
        {feedback && (
          <p className="notice success" role="status" style={{ marginBottom: 22 }}>
            {feedback}
          </p>
        )}
        {remove.isError && (
          <p className="notice error" role="alert" style={{ marginBottom: 22 }}>
            {errorMessage(remove.error)}
          </p>
        )}
        {query.isPending && <Loading label="Abrindo sua biblioteca…" />}
        {query.isError && <Failure error={query.error} retry={() => void query.refetch()} />}
        {query.data?.items.length === 0 && (
          <Empty
            title="O próximo golaço merece ficar"
            action={
              <Link href="/arenas" className="button">
                Encontrar meus lances
              </Link>
            }
          >
            Encontre o lance da sua quadra e escolha “Salvar na biblioteca”. Ele aparece aqui só
            para você.
          </Empty>
        )}
        {!!query.data?.items.length && (
          <div className={styles.libraryGrid}>
            {query.data.items.map((replay) => (
              <ReplayCard
                key={replay.evento_id}
                replay={replay}
                zone="America/Sao_Paulo"
                remove={() => {
                  setFeedback(undefined);
                  remove.mutate(replay.evento_id);
                }}
                pending={remove.isPending && remove.variables === replay.evento_id}
              />
            ))}
          </div>
        )}
        {query.data && (
          <Pagination page={page} size={12} total={query.data.total} change={setPage} />
        )}
        <p className={styles.libraryNote}>
          <Bookmark size={17} aria-hidden="true" />
          Sua biblioteca é pessoal. Horários exibidos em Brasília. Lances retirados pela arena não
          ficam disponíveis.
        </p>
      </div>
    </PlayerGate>
  );
}
