"use client";

import { useState, type MouseEvent } from "react";
import useSWR from "swr";
import type { ExternalTrack } from "@/lib/contracts/music";

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

  const { data, isLoading } = useSWR<{ tracks: EnrichedTrack[]; error?: string }>(
    submitted ? `/api/search?q=${encodeURIComponent(submitted)}` : null,
    fetcher
  );

  const { data: health } = useSWR("/api/providers/youtube-music/health", fetcher, { refreshInterval: 30000 });
  const [playing, setPlaying] = useState<EnrichedTrack | null>(null);
  const [streamUrl, setStreamUrl] = useState<string | null>(null);
  const [streamError, setStreamError] = useState<string | null>(null);
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

  async function play(track: EnrichedTrack) {
    setPlaying(track);
    setStreamUrl(null);
    setStreamError(null);

    fetch("/api/events", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...externalPayload(track), type: "PLAY_START", source: track.localMatch ? "LOCAL" : "PROVIDER" }),
    });

    if (track.localMatch) return;

    const res = await fetch(`/api/play/${track.providerTrackId}`);
    const body = await res.json();
    if (!res.ok) {
      setStreamError(`${body.error} (${body.status})`);
    } else {
      setStreamUrl(body.url);
    }
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

      {playing && (
        <div className="rounded-xl border border-[var(--brand)]/40 bg-[var(--panel)] p-3">
          <div className="mb-2 text-sm font-semibold">
            {playing.title} — {playing.artist} <span className="text-[var(--brand)]">{playing.localMatch ? "· Local" : "· Streaming"}</span>
          </div>
          {playing.localMatch ? (
            // eslint-disable-next-line jsx-a11y/media-has-caption
            <audio controls autoPlay src={`/api/stream/${playing.localMatch.fileId}`} className="w-full" />
          ) : streamUrl ? (
            // eslint-disable-next-line jsx-a11y/media-has-caption
            <audio controls autoPlay src={streamUrl} className="w-full" />
          ) : streamError ? (
            <p className="text-sm text-red-400">{streamError}</p>
          ) : (
            <p className="text-sm text-[var(--ink-dim)]">Résolution du flux…</p>
          )}
        </div>
      )}

      <ul className="flex flex-col gap-2">
        {data?.tracks?.map((t) => (
          <li
            key={t.providerTrackId}
            onClick={() => play(t)}
            className="flex cursor-pointer items-center gap-3 rounded-xl border border-white/10 bg-[var(--panel)] p-3 hover:border-[var(--brand)]/50"
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
        ))}
      </ul>
    </main>
  );
}
