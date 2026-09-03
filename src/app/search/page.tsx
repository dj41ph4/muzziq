"use client";

import { useMemo, useState, type MouseEvent } from "react";
import useSWR from "swr";
import { Search as SearchIcon, Plus, Check, Music2, Play, User, Disc3, X } from "lucide-react";
import type { ExternalTrack } from "@/lib/contracts/music";
import { usePlayer, type PlayableTrack } from "@/components/PlayerContext";
import { TopBar } from "@/components/TopBar";

type EnrichedTrack = ExternalTrack & { localMatch?: { fileId: string; confidence: number } };

interface DerivedArtist {
  name: string;
  thumbnailUrl?: string;
  trackCount: number;
}

interface DerivedAlbum {
  title: string;
  artist: string;
  thumbnailUrl?: string;
  trackCount: number;
}

interface SearchResponse {
  tracks: EnrichedTrack[];
  artists: DerivedArtist[];
  albums: DerivedAlbum[];
  error?: string;
}

const fetcher = (url: string) => fetch(url).then((r) => r.json());

function formatDuration(seconds?: number): string {
  if (!seconds) return "";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function statusColor(status?: string): string {
  if (status === "OK") return "text-[var(--brand)]";
  if (status === "DEGRADED" || status === "AUTH_REQUIRED" || status === "RATE_LIMITED") return "text-amber-400";
  return "text-red-400";
}

function albumKey(title: string, artist: string): string {
  return `${title.trim().toLowerCase()}::${artist.trim().toLowerCase()}`;
}

export default function SearchPage() {
  const [query, setQuery] = useState("");
  const [submitted, setSubmitted] = useState("");
  const [albumFilter, setAlbumFilter] = useState<DerivedAlbum | null>(null);
  const { play, track: nowPlaying, isPlaying } = usePlayer();

  const { data, isLoading } = useSWR<SearchResponse>(
    submitted ? `/api/search?q=${encodeURIComponent(submitted)}` : null,
    fetcher
  );

  const { data: health } = useSWR("/api/providers/youtube-music/health", fetcher, { refreshInterval: 30000 });
  const [addedIds, setAddedIds] = useState<Set<string>>(new Set());

  function runSearch(text: string) {
    setQuery(text);
    setSubmitted(text);
    setAlbumFilter(null);
  }

  const visibleTracks = useMemo(() => {
    const all = data?.tracks ?? [];
    if (!albumFilter) return all;
    const key = albumKey(albumFilter.title, albumFilter.artist);
    return all.filter((t) => t.album && albumKey(t.album, t.artist) === key);
  }, [data?.tracks, albumFilter]);

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

  function toPlayable(t: EnrichedTrack): PlayableTrack {
    return t.localMatch
      ? { kind: "local", id: t.localMatch.fileId, title: t.title, artist: t.artist, album: t.album, thumbnailUrl: t.thumbnailUrl, durationSeconds: t.durationSeconds }
      : { kind: "provider", id: t.providerTrackId, title: t.title, artist: t.artist, album: t.album, thumbnailUrl: t.thumbnailUrl, durationSeconds: t.durationSeconds };
  }

  function playTrack(t: EnrichedTrack) {
    const queue = visibleTracks.map(toPlayable);
    play(toPlayable(t), queue);
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
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <div className="flex items-center justify-between">
        <TopBar title="Recherche" />
        {health && (
          <span className="glass flex items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-semibold text-[var(--ink-soft)]">
            <span className={`h-1.5 w-1.5 rounded-full ${statusColor(health.probes?.player?.status)} bg-current`} />
            YT Music
          </span>
        )}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          runSearch(query.trim());
        }}
        className="float-in relative"
      >
        <SearchIcon size={18} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-[var(--ink-dim)]" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Artiste, titre, album…"
          className="glass w-full rounded-full py-3.5 pl-11 pr-16 text-[15px] outline-none placeholder:text-[var(--ink-dim)] focus:border-[var(--brand)]/50"
        />
        <button
          type="submit"
          disabled={!query.trim()}
          className="brand-gradient absolute right-1.5 top-1/2 -translate-y-1/2 rounded-full px-4 py-2 text-[13px] font-bold text-black transition-transform active:scale-95 disabled:opacity-0"
        >
          OK
        </button>
      </form>

      {isLoading && (
        <div className="flex flex-col gap-2">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="flex animate-pulse items-center gap-3 rounded-xl p-3">
              <div className="h-12 w-12 rounded-lg bg-white/[0.06]" />
              <div className="flex-1">
                <div className="h-3 w-2/5 rounded bg-white/[0.06]" />
                <div className="mt-1.5 h-2.5 w-1/3 rounded bg-white/[0.04]" />
              </div>
            </div>
          ))}
        </div>
      )}
      {data?.error && <p className="text-sm text-red-400">{data.error}</p>}

      {!!data?.artists?.length && (
        <section className="float-in">
          <div className="mb-3 flex items-center gap-2 text-[var(--ink-dim)]">
            <User size={14} />
            <h2 className="text-[13px] font-bold uppercase tracking-wide">Artistes</h2>
          </div>
          <div className="-mx-5 flex gap-4 overflow-x-auto px-5 pb-1 sm:-mx-8 sm:px-8" style={{ scrollbarWidth: "none" }}>
            {data.artists.slice(0, 10).map((a) => (
              <button
                key={a.name}
                onClick={() => runSearch(a.name)}
                title={`Rechercher "${a.name}"`}
                className="flex w-24 flex-shrink-0 flex-col items-center gap-2 text-center"
              >
                <div className="art-fallback flex h-24 w-24 items-center justify-center overflow-hidden rounded-full shadow-[var(--shadow-card)]">
                  {a.thumbnailUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={a.thumbnailUrl} alt="" className="h-full w-full object-cover" />
                  ) : (
                    <User size={26} className="text-white/20" />
                  )}
                </div>
                <span className="truncate text-[12px] font-semibold tracking-tight">{a.name}</span>
              </button>
            ))}
          </div>
        </section>
      )}

      {!!data?.albums?.length && (
        <section className="float-in">
          <div className="mb-3 flex items-center gap-2 text-[var(--ink-dim)]">
            <Disc3 size={14} />
            <h2 className="text-[13px] font-bold uppercase tracking-wide">Albums</h2>
          </div>
          <div className="-mx-5 flex gap-4 overflow-x-auto px-5 pb-1 sm:-mx-8 sm:px-8" style={{ scrollbarWidth: "none" }}>
            {data.albums.slice(0, 10).map((al) => {
              const active = albumFilter && albumKey(albumFilter.title, albumFilter.artist) === albumKey(al.title, al.artist);
              return (
                <button
                  key={albumKey(al.title, al.artist)}
                  onClick={() => setAlbumFilter(active ? null : al)}
                  className="w-32 flex-shrink-0 text-left"
                >
                  <div
                    className={`art-fallback aspect-square w-full overflow-hidden rounded-xl shadow-[var(--shadow-card)] ${active ? "ring-2 ring-[var(--brand)]" : ""}`}
                  >
                    {al.thumbnailUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={al.thumbnailUrl} alt="" className="h-full w-full object-cover" />
                    ) : (
                      <div className="flex h-full w-full items-center justify-center">
                        <Disc3 size={26} className="text-white/20" />
                      </div>
                    )}
                  </div>
                  <div className={`mt-2 truncate text-[13px] font-semibold tracking-tight ${active ? "text-[var(--brand)]" : ""}`}>{al.title}</div>
                  <div className="truncate text-[12px] text-[var(--ink-soft)]">{al.artist}</div>
                </button>
              );
            })}
          </div>
        </section>
      )}

      {!!data?.tracks?.length && (
        <section className="float-in flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-[var(--ink-dim)]">
              <Music2 size={14} />
              <h2 className="text-[13px] font-bold uppercase tracking-wide">Titres ({visibleTracks.length})</h2>
            </div>
            {albumFilter && (
              <button
                onClick={() => setAlbumFilter(null)}
                className="flex items-center gap-1.5 rounded-full border border-[var(--brand)]/30 bg-[var(--brand)]/12 px-3 py-1 text-[11px] font-bold text-[var(--brand)]"
              >
                {albumFilter.title}
                <X size={12} />
              </button>
            )}
          </div>

          <ul className="flex flex-col gap-1">
            {visibleTracks.map((t, i) => {
              const isCurrent = nowPlaying?.id === (t.localMatch?.fileId ?? t.providerTrackId);
              return (
                <li
                  key={t.providerTrackId}
                  onClick={() => playTrack(t)}
                  className="group flex cursor-pointer items-center gap-3 rounded-xl p-2.5 transition-colors hover:bg-white/[0.04]"
                  style={{ animationDelay: `${Math.min(i, 12) * 25}ms` }}
                >
                  <div className="relative h-12 w-12 flex-shrink-0 overflow-hidden rounded-lg shadow-[var(--shadow-card)]">
                    {t.thumbnailUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={t.thumbnailUrl} alt="" className="h-full w-full object-cover" />
                    ) : (
                      <div className="art-fallback flex h-full w-full items-center justify-center">
                        <Music2 size={16} className="text-white/25" />
                      </div>
                    )}
                    <div className="absolute inset-0 flex items-center justify-center bg-black/45 opacity-0 transition-opacity group-hover:opacity-100">
                      <Play size={16} fill="white" className="text-white" />
                    </div>
                  </div>

                  <div className="min-w-0 flex-1">
                    <div className={`truncate text-[14px] font-semibold tracking-tight ${isCurrent ? "text-[var(--brand)]" : ""}`}>
                      {isCurrent && isPlaying && <span className="mr-1 text-[var(--brand)]">▸</span>}
                      {t.title}
                    </div>
                    <div className="truncate text-[13px] text-[var(--ink-soft)]">
                      {t.artist}
                      {t.album ? ` • ${t.album}` : ""}
                    </div>
                  </div>

                  {t.localMatch && (
                    <span className="rounded-full border border-[var(--brand)]/30 bg-[var(--brand)]/12 px-2 py-0.5 text-[10px] font-bold text-[var(--brand)]">
                      Local
                    </span>
                  )}
                  <span className="hidden font-mono text-[11px] tabular-nums text-[var(--ink-dim)] sm:inline">
                    {formatDuration(t.durationSeconds)}
                  </span>
                  <button
                    onClick={(e) => addToLibrary(e, t)}
                    disabled={addedIds.has(t.providerTrackId)}
                    title="Ajouter à la bibliothèque"
                    className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-[var(--ink-dim)] transition-colors hover:bg-white/10 hover:text-[var(--brand)] disabled:text-[var(--brand)]"
                  >
                    {addedIds.has(t.providerTrackId) ? <Check size={16} /> : <Plus size={16} />}
                  </button>
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </main>
  );
}
