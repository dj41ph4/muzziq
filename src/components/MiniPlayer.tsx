"use client";

import { Play, Pause, Loader2, Music2 } from "lucide-react";
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
    <div className="glass float-in fixed inset-x-0 bottom-14 z-40 border-x-0 border-b-0 shadow-[var(--shadow-float)] lg:bottom-0 lg:left-64">
      <div
        className="group/bar relative h-1 cursor-pointer bg-white/[0.06]"
        onClick={(e) => {
          if (!duration) return;
          const rect = e.currentTarget.getBoundingClientRect();
          seek(((e.clientX - rect.left) / rect.width) * duration);
        }}
      >
        <div className="h-full bg-[var(--brand)] transition-[width] duration-150" style={{ width: `${pct}%` }} />
        <div
          className="pointer-events-none absolute top-1/2 h-2.5 w-2.5 -translate-y-1/2 rounded-full bg-white opacity-0 shadow transition-opacity group-hover/bar:opacity-100"
          style={{ left: `calc(${pct}% - 5px)` }}
        />
      </div>

      <div className="mx-auto flex max-w-3xl items-center gap-3 px-4 py-2.5">
        <div className="relative h-11 w-11 flex-shrink-0 overflow-hidden rounded-lg shadow-[var(--shadow-card)]">
          {track.thumbnailUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={track.thumbnailUrl} alt="" className="h-full w-full object-cover" />
          ) : (
            <div className="art-fallback flex h-full w-full items-center justify-center">
              <Music2 size={16} className="text-white/25" />
            </div>
          )}
          {isPlaying && (
            <div className="absolute inset-0 flex items-end justify-center gap-[2px] bg-black/35 pb-1.5">
              {[0, 1, 2].map((i) => (
                <span
                  key={i}
                  className="pulse-live w-[2.5px] rounded-full bg-[var(--brand)]"
                  style={{ height: 8, animationDelay: `${i * 0.15}s` }}
                />
              ))}
            </div>
          )}
        </div>

        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold tracking-tight">{track.title}</div>
          <div className="truncate text-[13px] text-[var(--ink-soft)]">
            {error ? <span className="text-red-400">{error}</span> : track.artist}
          </div>
        </div>

        <span className="hidden font-mono text-[11px] tabular-nums text-[var(--ink-dim)] sm:inline">
          {fmt(progress)} / {fmt(duration)}
        </span>

        <button
          onClick={togglePlay}
          disabled={isLoading}
          className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-white text-black shadow-[var(--shadow-card)] transition-transform active:scale-90 disabled:opacity-60"
        >
          {isLoading ? (
            <Loader2 size={18} className="animate-spin" />
          ) : isPlaying ? (
            <Pause size={18} fill="currentColor" />
          ) : (
            <Play size={18} fill="currentColor" className="ml-0.5" />
          )}
        </button>
      </div>
    </div>
  );
}
