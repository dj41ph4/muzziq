"use client";

import { useState } from "react";
import useSWR from "swr";
import type { ExternalTrack } from "@/lib/contracts/music";

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

  const { data, isLoading } = useSWR<{ tracks: ExternalTrack[]; error?: string }>(
    submitted ? `/api/search?q=${encodeURIComponent(submitted)}` : null,
    fetcher
  );

  const { data: health } = useSWR("/api/providers/youtube-music/health", fetcher, { refreshInterval: 30000 });

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
        {data?.tracks?.map((t) => (
          <li
            key={t.providerTrackId}
            className="flex items-center gap-3 rounded-xl border border-white/10 bg-[var(--panel)] p-3"
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
            <span className="text-xs text-[var(--ink-dim)]">{formatDuration(t.durationSeconds)}</span>
          </li>
        ))}
      </ul>
    </main>
  );
}
