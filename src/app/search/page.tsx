"use client";

import { useState, type MouseEvent } from "react";
import useSWR from "swr";
import type { ExternalTrack } from "@/lib/contracts/music";
import { usePlayer, type PlayableTrack } from "@/components/PlayerContext";

type EnrichedTrack = ExternalTrack & { localMatch?: { fileId: string; confidence: number } };

const fetcher = (url: string) => fetch(url).then((r) => r.json());

function formatDuration(seconds?: number): string {
  if (!seconds) return "";
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

export default function SearchPage() {
  const [query, setQuery] = useState("");
  const [submitted, setSubmitted] = useState("");
  const { play, track: nowPlaying } = usePlayer();

  const { data, isLoading } = useSWR<{ tracks: EnrichedTrack[]; error?: string }>(
    submitted ? `/api/search?q=${encodeURIComponent(submitted)}` : null,
    fetcher
  );

  const { data: health } = useSWR("/api/providers/youtube-music/health", fetcher, { refreshInterval: 30000 });
  const [addedIds, setAddedIds] = useState<Set<string>>(new Set());

  function externalPayload(t: EnrichedTrack) {
    return {
      provider: "youtube-music",
      providerTrackId: t.providerTrackId,
      title: t.title,
      artist: t.artist,
      album: t.album,
      durationSeconds: t.durationSeconds,
      thumbnailUrl: t.thumbnailUrl,
    };
  }

  function playTrack(t: EnrichedTrack) {
    const playable: PlayableTrack = t.localMatch
      ? { kind: "local", id: t.localMatch.fileId, title: t.title, artist: t.artist, album: t.album, thumbnailUrl: t.thumbnailUrl, durationSeconds: t.durationSeconds }
      : { kind: "provider", id: t.providerTrackId, title: t.title, artist: t.artist, album: t.album, thumbnailUrl: t.thumbnailUrl, durationSeconds: t.durationSeconds };
    play(playable);
  }

  async function addToLibrary(e: MouseEvent, track: EnrichedTrack) {
    e.stopPropagation();
    await fetch("/api/library/items", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(externalPayload(track)),
    });
    setAddedIds((prev) => new Set(prev).add(track.providerTrackId));
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-6 py-10">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Recherche</h1>
        {health && (
          <span className="rounded-full border border-white/15 bg-[var(--panel)] px-2.5 py-0.5 text-[10px] font-bold text-[var(--ink-soft)]">
            YT Music — recherche {health.probes?.search?.status} · lecture {health.probes?.player?.status}
          </span>
        )}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setSubmitted(query.trim());
        }}
        className="flex gap-2"
      >
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Artiste, titre, album…"
          className="flex-1 rounded-xl border border-white/10 bg-[var(--panel)] px-4 py-2 outline-none focus:border-[var(--brand)]"
        />
        <button type="submit" className="rounded-xl bg-[var(--brand)] px-4 py-2 font-bold text-black">
          Chercher
        </button>
      </form>

      {isLoading && <p className="text-[var(--ink-dim)]">Recherche en cours…</p>}
      {data?.error && <p className="text-red-400">{data.error}</p>}

      <ul className="flex flex-col gap-2">
        {data?.tracks?.map((t) => {
          const isCurrent = nowPlaying?.id === (t.localMatch?.fileId ?? t.providerTrackId);
          return (
            <li
              key={t.providerTrackId}
              onClick={() => playTrack(t)}
              className={`flex cursor-pointer items-center gap-3 rounded-xl border p-3 hover:border-[var(--brand)]/50 ${
                isCurrent ? "border-[var(--brand)]/60 bg-[var(--brand)]/5" : "border-white/10 bg-[var(--panel)]"
              }`}
            >
              {t.thumbnailUrl && (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={t.thumbnailUrl} alt="" className="h-12 w-12 rounded-lg object-cover" />
              )}
              <div className="min-w-0 flex-1">
                <div className="truncate font-semibold">{t.title}</div>
                <div className="truncate text-sm text-[var(--ink-soft)]">
                  {t.artist}
                  {t.album ? ` • ${t.album}` : ""}
                </div>
              </div>
              {t.localMatch && (
                <span className="rounded-full border border-[var(--brand)]/30 bg-[var(--brand)]/12 px-2 py-0.5 text-[10px] font-bold text-[var(--brand)]">
                  Local
                </span>
              )}
              <span className="text-xs text-[var(--ink-dim)]">{formatDuration(t.durationSeconds)}</span>
              <button
                onClick={(e) => addToLibrary(e, t)}
                disabled={addedIds.has(t.providerTrackId)}
                title="Ajouter à la bibliothèque"
                className="rounded-full border border-white/15 px-2 py-1 text-xs font-bold text-[var(--ink-soft)] hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-40"
              >
                {addedIds.has(t.providerTrackId) ? "✓" : "+"}
              </button>
            </li>
          );
        })}
      </ul>
    </main>
  );
}
