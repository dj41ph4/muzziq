"use client";

import { use } from "react";
import useSWR from "swr";
import { X, Music2, Play, Pause } from "lucide-react";
import { usePlayer, type PlayableTrack } from "@/components/PlayerContext";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface PlaylistItemView {
  id: string;
  recordingId: string;
  recording?: { title: string; artist: string; album?: string; thumbnailUrl?: string; durationSeconds?: number };
}

function fmtDuration(seconds?: number): string {
  if (!seconds) return "—";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

export default function PlaylistDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { data, mutate } = useSWR<{ name: string; items: PlaylistItemView[] }>(`/api/playlists/${id}`, fetcher);
  const { play, togglePlay, track: nowPlaying, isPlaying } = usePlayer();

  async function removeItem(itemId: string) {
    await fetch(`/api/playlists/${id}/items?itemId=${itemId}`, { method: "DELETE" });
    mutate();
  }

  async function resolveOne(item: PlaylistItemView): Promise<PlayableTrack | null> {
    const r = item.recording;
    if (!r) return null;
    const res = await fetch(`/api/recordings/${item.recordingId}/resolve`);
    if (!res.ok) return null; // Playback Resolver n'a trouvé aucune source réelle — jamais fabriquer une lecture qui ne marchera pas.
    const resolved: { kind: "local" | "provider"; id: string } = await res.json();
    return { kind: resolved.kind, id: resolved.id, title: r.title, artist: r.artist, album: r.album, thumbnailUrl: r.thumbnailUrl, durationSeconds: r.durationSeconds };
  }

  async function playItem(item: PlaylistItemView) {
    if (!data) return;
    const resolvedItems = await Promise.all(data.items.map(resolveOne));
    const queue = resolvedItems.filter((t): t is PlayableTrack => t !== null);
    const clickedIdx = data.items.findIndex((x) => x.id === item.id);
    const target = resolvedItems[clickedIdx];
    if (!target || queue.length === 0) return;
    play(target, queue);
  }

  async function playAll() {
    if (!data || data.items.length === 0) return;
    await playItem(data.items[0]);
  }

  if (!data) return null;

  const totalSeconds = data.items.reduce((sum, it) => sum + (it.recording?.durationSeconds ?? 0), 0);
  const covers = data.items.map((it) => it.recording?.thumbnailUrl).filter(Boolean).slice(0, 4) as string[];

  return (
    <main className="mx-auto flex min-h-screen max-w-5xl flex-col gap-8 px-5 pt-8 sm:px-8">
      <header className="float-in flex flex-col items-center gap-5 sm:flex-row sm:items-end">
        <div className="grid h-40 w-40 flex-shrink-0 grid-cols-2 grid-rows-2 overflow-hidden rounded-xl shadow-[var(--shadow-card)] sm:h-48 sm:w-48">
          {covers.length > 0 ? (
            covers.map((url, i) => (
              // eslint-disable-next-line @next/next/no-img-element
              <img key={i} src={url} alt="" className="h-full w-full object-cover" />
            ))
          ) : (
            <div className="art-fallback col-span-2 row-span-2 flex items-center justify-center">
              <Music2 size={40} className="text-white/20" />
            </div>
          )}
        </div>
        <div className="flex flex-1 flex-col items-center gap-2 text-center sm:items-start sm:text-left">
          <span className="text-[11px] font-bold uppercase tracking-[0.2em] text-[var(--ink-dim)]">Playlist</span>
          <h1 className="text-3xl font-extrabold tracking-tight sm:text-5xl">{data.name}</h1>
          <p className="text-[13px] text-[var(--ink-soft)]">
            {data.items.length} morceau{data.items.length > 1 ? "x" : ""}
            {totalSeconds > 0 ? ` • ${Math.round(totalSeconds / 60)} min` : ""}
          </p>
        </div>
      </header>

      {data.items.length > 0 && (
        <button
          onClick={playAll}
          className="brand-gradient float-in flex h-14 w-14 flex-shrink-0 items-center justify-center self-center rounded-full text-black shadow-[0_8px_24px_-6px_var(--brand-glow)] transition-transform active:scale-90 sm:self-start"
        >
          <Play size={22} fill="currentColor" className="ml-1" />
        </button>
      )}

      {/* En-têtes de tableau — masqués en mobile, la ligne reste lisible en carte compacte. */}
      <div className="hidden grid-cols-[2rem_1fr_1fr_5rem] gap-4 border-b border-[var(--stroke)] px-3 pb-2 text-[11px] font-bold uppercase tracking-wide text-[var(--ink-dim)] sm:grid">
        <span>#</span>
        <span>Titre</span>
        <span>Album</span>
        <span className="text-right">Durée</span>
      </div>

      <ul className="-mt-4 flex flex-col">
        {data.items.map((item, i) => {
          const r = item.recording;
          if (!r) return null;
          const isCurrent = nowPlaying?.kind && nowPlaying.title === r.title && nowPlaying.artist === r.artist;
          return (
            <li
              key={item.id}
              className={`group grid grid-cols-[2rem_1fr_5rem] items-center gap-4 rounded-xl px-3 py-2.5 transition-colors hover:bg-white/[0.04] sm:grid-cols-[2rem_1fr_1fr_5rem] ${isCurrent ? "bg-[var(--brand)]/[0.07]" : ""}`}
            >
              <div className="flex w-8 items-center justify-center">
                <span className={`text-[13px] tabular-nums text-[var(--ink-dim)] group-hover:hidden ${isCurrent && isPlaying ? "hidden" : ""}`}>
                  {i + 1}
                </span>
                <button
                  onClick={() => (isCurrent ? togglePlay() : playItem(item))}
                  className={`hidden h-6 w-6 items-center justify-center text-[var(--ink)] group-hover:flex ${isCurrent && isPlaying ? "!flex" : ""}`}
                >
                  {isCurrent && isPlaying ? <Pause size={14} fill="currentColor" /> : <Play size={14} fill="currentColor" />}
                </button>
              </div>

              <div className="flex min-w-0 items-center gap-3">
                <div
                  onClick={() => (isCurrent ? togglePlay() : playItem(item))}
                  className="art-fallback flex h-11 w-11 flex-shrink-0 cursor-pointer items-center justify-center overflow-hidden rounded-lg"
                >
                  {r.thumbnailUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={r.thumbnailUrl} alt="" className="h-full w-full object-cover" />
                  ) : (
                    <Music2 size={16} className="text-white/25" />
                  )}
                </div>
                <div className="min-w-0">
                  <div className={`truncate text-[14px] font-semibold ${isCurrent ? "text-[var(--brand)]" : ""}`}>{r.title}</div>
                  <div className="truncate text-[13px] text-[var(--ink-soft)]">{r.artist}</div>
                </div>
              </div>

              <div className="hidden min-w-0 truncate text-[13px] text-[var(--ink-soft)] sm:block">{r.album ?? "—"}</div>

              <div className="flex items-center justify-end gap-2">
                <span className="font-mono text-[12px] tabular-nums text-[var(--ink-dim)]">{fmtDuration(r.durationSeconds)}</span>
                <button
                  onClick={() => removeItem(item.id)}
                  className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full text-[var(--ink-dim)] opacity-0 transition-opacity hover:bg-red-500/10 hover:text-red-400 group-hover:opacity-100"
                >
                  <X size={14} />
                </button>
              </div>
            </li>
          );
        })}
      </ul>
      {data.items.length === 0 && (
        <p className="glass rounded-xl px-4 py-6 text-center text-sm text-[var(--ink-dim)]">
          Playlist vide — ajoute des morceaux depuis la recherche ou la bibliothèque.
        </p>
      )}
    </main>
  );
}
