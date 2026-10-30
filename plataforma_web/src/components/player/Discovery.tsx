"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowUpRight, MapPin, Search } from "lucide-react";
import { useState } from "react";
import { platform } from "@/lib/platform/api";
import { useSession } from "@/lib/platform/session";
import { Back, Empty, Failure, Loading, Pagination, PitchArtwork, PlayerGate } from "./shared";
import styles from "./Player.module.css";

export function ArenasView({ initialSearch = "" }: { initialSearch?: string }) {
  const { user } = useSession("player");
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState(initialSearch);
  const query = useQuery({
    queryKey: ["player", "arenas", user?.usuario_id, page],
    queryFn: () => platform.arenas(page, 20),
    enabled: !!user,
  });
  const visible = query.data?.items.filter((arena) =>
    arena.nome.toLocaleLowerCase("pt-BR").includes(search.toLocaleLowerCase("pt-BR")),
  );
  return (
    <PlayerGate>
      <div className={styles.page}>
        <header className={styles.heading}>
          <div>
            <h1 className={styles.title}>Onde foi o jogo?</h1>
            <p className={styles.intro}>
              Encontre a arena, escolha sua quadra e volte ao lance que vale ver de novo.
            </p>
          </div>
        </header>
        <div className={styles.tools}>
          <label className={styles.search}>
            <Search aria-hidden="true" />
            <span className="sr-only">Filtrar arenas desta página pelo nome</span>
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Qual é o nome da arena?"
            />
          </label>
          {query.data && (
            <span className={styles.count}>
              {query.data.total}{" "}
              {query.data.total === 1 ? "arena disponível" : "arenas disponíveis"}
              {search && " • filtro nesta página"}
            </span>
          )}
        </div>
        {query.isPending && <Loading label="Encontrando as arenas…" />}
        {query.isError && <Failure error={query.error} retry={() => void query.refetch()} />}
        {visible?.length === 0 && (
          <Empty title={search ? "Nenhuma arena com esse nome" : "As arenas vão aparecer aqui"}>
            {search
              ? "Confira o nome ou limpe a busca para ver as arenas desta página."
              : "Quando uma arena estiver disponível, você poderá escolher a quadra e encontrar seus lances."}
          </Empty>
        )}
        {!!visible?.length && (
          <div className={styles.arenaGrid}>
            {visible.map((arena) => (
              <Link
                className={styles.arena}
                href={`/arenas/${encodeURIComponent(arena.empresa_id)}?nome=${encodeURIComponent(arena.nome)}`}
                key={arena.empresa_id}
              >
                <div className={styles.arenaArt}>
                  <PitchArtwork />
                </div>
                <div className={styles.arenaCaption}>
                  <div>
                    <h2>{arena.nome}</h2>
                    <p>Encontrar minha quadra</p>
                  </div>
                  <span className={styles.roundArrow}>
                    <ArrowUpRight aria-hidden="true" />
                  </span>
                </div>
              </Link>
            ))}
          </div>
        )}
        {query.data && (
          <Pagination
            page={page}
            size={20}
            total={query.data.total}
            change={(next) => {
              setPage(next);
              setSearch("");
            }}
          />
        )}
      </div>
    </PlayerGate>
  );
}

export function ArenaView({ id, name }: { id: string; name?: string }) {
  const { user } = useSession("player");
  const [page, setPage] = useState(0);
  const courts = useQuery({
    queryKey: ["player", "courts", user?.usuario_id, id, page],
    queryFn: () => platform.courts(id, page, 20),
    enabled: !!user,
  });
  const arenas = useQuery({
    queryKey: ["player", "arena-name", user?.usuario_id, id],
    queryFn: () => platform.arenas(0, 100),
    enabled: !!user && !name,
  });
  const title =
    name || arenas.data?.items.find((item) => item.empresa_id === id)?.nome || "Escolha sua quadra";
  return (
    <PlayerGate>
      <div className={styles.page}>
        <Back href="/arenas">Todas as arenas</Back>
        <header className={styles.venueHero}>
          <div>
            <h1 className={styles.title}>{title}</h1>
            <p className={styles.intro}>
              Em qual quadra você jogou? Escolha abaixo para encontrar os dias e horários dos seus
              lances.
            </p>
          </div>
          <div className={styles.venueArt}>
            <PitchArtwork />
          </div>
        </header>
        {courts.isPending && <Loading label="Carregando as quadras…" />}
        {courts.isError && <Failure error={courts.error} retry={() => void courts.refetch()} />}
        {courts.data?.items.length === 0 && (
          <Empty title="Nenhuma quadra disponível">
            A arena ainda não tem quadras disponíveis para consulta. Volte mais tarde ou encontre
            outra arena.
          </Empty>
        )}
        {!!courts.data?.items.length && (
          <div className={styles.courtList}>
            {courts.data.items.map((court) => (
              <Link
                key={court.quadra_id}
                className={styles.court}
                href={`/quadras/${encodeURIComponent(court.quadra_id)}?nome=${encodeURIComponent(court.nome)}&arena=${encodeURIComponent(title)}`}
              >
                <span className={styles.courtIcon}>
                  <MapPin aria-hidden="true" />
                </span>
                <div>
                  <h2>{court.nome}</h2>
                  <p>Escolher data e horário</p>
                </div>
                <span className={styles.roundArrow}>
                  <ArrowUpRight aria-hidden="true" />
                </span>
              </Link>
            ))}
          </div>
        )}
        {courts.data && (
          <Pagination page={page} size={20} total={courts.data.total} change={setPage} />
        )}
      </div>
    </PlayerGate>
  );
}
