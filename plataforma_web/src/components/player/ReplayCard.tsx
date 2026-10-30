"use client";

import Link from "next/link";
import { Bookmark, Play, Trash2 } from "lucide-react";
import type { Replay } from "@/lib/platform/types";
import { localDateTime, PitchArtwork } from "./shared";
import styles from "./Player.module.css";

export function ReplayCard({
  replay,
  zone,
  remove,
  pending,
}: {
  replay: Replay;
  zone?: string;
  remove?: () => void;
  pending?: boolean;
}) {
  const time = localDateTime(replay.timestamp, zone, {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
  const day = localDateTime(replay.timestamp, zone, { day: "2-digit", month: "long" });
  return (
    <article className={styles.replayCard}>
      <Link
        className={styles.replayLink}
        href={`/lances/${encodeURIComponent(replay.evento_id)}${zone ? `?fuso=${encodeURIComponent(zone)}` : ""}`}
        aria-label={`Assistir ao lance de ${day}, às ${time}`}
      >
        <div className={styles.replayArt}>
          <PitchArtwork />
          <span className={styles.replayPlay}>
            <Play aria-hidden="true" fill="currentColor" />
          </span>
          <span className={styles.replayTime}>{time}</span>
          {replay.salvo && (
            <span className={styles.saved} title="Na sua biblioteca">
              <Bookmark fill="currentColor" aria-label="Salvo" />
            </span>
          )}
        </div>
      </Link>
      <div className={styles.replayInfo}>
        <div>
          <h3>Seu lance das {time.slice(0, 5)}</h3>
          <p>
            {day}
            {replay.salvo ? " • Na biblioteca" : ""}
          </p>
        </div>
        {remove && (
          <button
            className={styles.tinyButton}
            onClick={remove}
            disabled={pending}
            aria-label={`Remover lance das ${time} da biblioteca`}
          >
            <Trash2 size={14} aria-hidden="true" />
            {pending ? "Removendo…" : "Remover"}
          </button>
        )}
      </div>
    </article>
  );
}
