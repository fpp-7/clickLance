"use client";

import { useQuery } from "@tanstack/react-query";
import { CalendarDays } from "lucide-react";
import { useState } from "react";
import { platform } from "@/lib/platform/api";
import { useSession } from "@/lib/platform/session";
import { Back, Empty, Failure, Loading, Pagination, PlayerGate } from "./shared";
import { ReplayCard } from "./ReplayCard";
import styles from "./Player.module.css";

export function CourtView({ id, name, arena }: { id: string; name?: string; arena?: string }) {
  const { user } = useSession("player");
  const [date, setDate] = useState<string>();
  const [hour, setHour] = useState<number>();
  const [page, setPage] = useState(0);
  const availability = useQuery({
    queryKey: ["player", "availability", user?.usuario_id, id, date],
    queryFn: () => platform.availability(id, date),
    enabled: !!user,
  });
  const activeDate = date || availability.data?.data;
  const replays = useQuery({
    queryKey: ["player", "replays", user?.usuario_id, id, activeDate, hour, page],
    queryFn: () =>
      platform.replays(id, {
        data: activeDate,
        hora_inicio: hour,
        hora_fim: hour === undefined ? undefined : hour + 1,
        page,
        size: 12,
      }),
    enabled: !!user && !!activeDate,
  });
  const zone = availability.data?.timezone || "America/Sao_Paulo";
  function selectDate(value: string) {
    setDate(value || undefined);
    setHour(undefined);
    setPage(0);
  }
  const formattedDate = activeDate
    ? new Intl.DateTimeFormat("pt-BR", {
        day: "numeric",
        month: "long",
        year: "numeric",
        timeZone: "UTC",
      }).format(new Date(`${activeDate}T12:00:00Z`))
    : "";
  return (
    <PlayerGate>
      <div className={styles.page}>
        <Back href="/arenas">Encontrar outra quadra</Back>
        <header className={styles.heading}>
          <div>
            {arena && <p className="eyebrow">{arena}</p>}
            <h1 className={styles.title}>{name || "Lances da quadra"}</h1>
            <p className={styles.intro}>Você lembra a hora. A gente guarda o lance.</p>
          </div>
          <CalendarDays size={29} color="var(--color-primary)" aria-hidden="true" />
        </header>
        {availability.isPending && <Loading label="Buscando datas e horários…" />}
        {availability.isError && (
          <Failure error={availability.error} retry={() => void availability.refetch()} />
        )}
        {availability.data && (
          <div className={styles.workspace}>
            <aside className={styles.filters} aria-label="Filtros dos lances">
              <h2>Encontre seu jogo</h2>
              <label className={styles.fieldLabel} htmlFor="replay-date">
                Dia em que você jogou
              </label>
              <input
                id="replay-date"
                className={styles.date}
                type="date"
                value={activeDate || ""}
                onChange={(event) => selectDate(event.target.value)}
              />
              {availability.data.datas.length > 0 && (
                <div className={styles.quickDates} aria-label="Datas com lances">
                  {availability.data.datas.slice(0, 4).map((day) => (
                    <button
                      key={day}
                      aria-pressed={activeDate === day}
                      onClick={() => selectDate(day)}
                    >
                      {day === availability.data.hoje
                        ? "Hoje"
                        : day.slice(5).split("-").reverse().join("/")}
                    </button>
                  ))}
                </div>
              )}
              <div className={styles.slots} aria-label="Horários disponíveis">
                <button
                  className={styles.slot}
                  aria-pressed={hour === undefined}
                  onClick={() => {
                    setHour(undefined);
                    setPage(0);
                  }}
                >
                  O dia inteiro
                </button>
                {availability.data.slots.map((slot) => (
                  <button
                    key={slot.hora_inicio}
                    className={styles.slot}
                    aria-pressed={hour === slot.hora_inicio}
                    onClick={() => {
                      setHour(slot.hora_inicio);
                      setPage(0);
                    }}
                  >
                    {String(slot.hora_inicio).padStart(2, "0")}h —{" "}
                    {String(slot.hora_fim).padStart(2, "0")}h<span>{slot.total}</span>
                  </button>
                ))}
              </div>
              <p className={styles.zone}>
                Horário local da quadra
                <br />
                {zone.replaceAll("_", " ")}
              </p>
            </aside>
            <section aria-label="Lances encontrados">
              <div className={styles.replayHeading}>
                <h2>{formattedDate}</h2>
                <p>
                  {replays.data
                    ? `${replays.data.total} ${replays.data.total === 1 ? "lance encontrado" : "lances encontrados"}`
                    : ""}
                </p>
              </div>
              {replays.isPending && <Loading />}
              {replays.isError && (
                <Failure error={replays.error} retry={() => void replays.refetch()} />
              )}
              {replays.data?.items.length === 0 && (
                <Empty title="Ainda não tem lance por aqui">
                  Experimente outro horário ou outra data. Lances novos aparecem depois que o vídeo
                  fica pronto.
                </Empty>
              )}
              {!!replays.data?.items.length && (
                <div className={styles.replayGrid}>
                  {replays.data.items.map((replay) => (
                    <ReplayCard key={replay.evento_id} replay={replay} zone={zone} />
                  ))}
                </div>
              )}
              {replays.data && (
                <Pagination page={page} size={12} total={replays.data.total} change={setPage} />
              )}
            </section>
          </div>
        )}
      </div>
    </PlayerGate>
  );
}
