"use client";

import { usePlayer } from "./PlayerContext";

function fmt(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

/** Mini-player persistant (langage Spotify §56.1) — fixe en bas, jamais démonté entre les pages. */
export function MiniPlayer() {
  const { track, isPlaying, isLoading, error, progress, duration, togglePlay, seek } = usePlayer();

  if (!track) return null;

  const pct = duration > 0 ? (progress / duration) * 100 : 0;

  return (
    <div className="fixed inset-x-0 bottom-14 z-40 border-t border-white/10 bg-[var(--panel)]/95 backdrop-blur">
      <div
        className="h-0.5 cursor-pointer bg-white/10"
        onClick={(e) => {
          if (!duration) return;
          const rect = e.currentTarget.getBoundingClientRect();
          seek(((e.clientX - rect.left) / rect.width) * duration);
        }}
      >
        <div className="h-full bg-[var(--brand)]" style={{ width: `${pct}%` }} />
      </div>
      <div className="mx-auto flex max-w-3xl items-center gap-3 px-4 py-2">
        <div className="h-10 w-10 flex-shrink-0 overflow-hidden rounded-lg bg-black/40">
          {track.thumbnailUrl && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={track.thumbnailUrl} alt="" className="h-full w-full object-cover" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold">{track.title}</div>
          <div className="truncate text-xs text-[var(--ink-soft)]">
            {track.artist} {error && <span className="text-red-400">· {error}</span>}
          </div>
        </div>
        <span className="hidden text-xs text-[var(--ink-dim)] sm:inline">
          {fmt(progress)} / {fmt(duration)}
        </span>
        <button
          onClick={togglePlay}
          disabled={isLoading}
          className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-[var(--brand)] text-black disabled:opacity-50"
        >
          {isLoading ? "…" : isPlaying ? "❚❚" : "▶"}
        </button>
      </div>
    </div>
  );
}
