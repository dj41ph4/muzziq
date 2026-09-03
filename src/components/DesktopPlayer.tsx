"use client";

import { Play, Pause, Loader2, Music2, SkipBack, SkipForward, Shuffle, Repeat, Repeat1, Volume2, Volume1, VolumeX } from "lucide-react";
import { usePlayer } from "./PlayerContext";
import { OfflineDownloadButton } from "./OfflineDownloadButton";

function fmt(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

/**
 * Barre de lecture desktop complète (langage Spotify §player desktop) —
 * rendue uniquement à partir de `lg`, à côté du MiniPlayer mobile (`lg:hidden`
 * sur celui-ci). Tous les contrôles affichés sont réellement câblés à
 * PlayerContext (file d'attente, shuffle, repeat) — jamais de bouton mort.
 */
export function DesktopPlayer() {
  const {
    track,
    isPlaying,
    isLoading,
    error,
    progress,
    duration,
    togglePlay,
    seek,
    next,
    previous,
    hasNext,
    hasPrevious,
    shuffle,
    repeat,
    toggleShuffle,
    cycleRepeat,
    volume,
    setVolume,
  } = usePlayer();

  if (!track) return null;

  const pct = duration > 0 ? (progress / duration) * 100 : 0;
  const VolumeIcon = volume === 0 ? VolumeX : volume < 0.5 ? Volume1 : Volume2;

  return (
    <div className="glass float-in fixed inset-x-0 bottom-0 z-40 hidden border-x-0 border-b-0 shadow-[var(--shadow-float)] lg:flex lg:flex-col">
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

      <div className="grid grid-cols-3 items-center gap-4 px-5 py-3">
        {/* Métadonnées piste */}
        <div className="flex min-w-0 items-center gap-3">
          <div className="relative h-14 w-14 flex-shrink-0 overflow-hidden rounded-lg shadow-[var(--shadow-card)]">
            {track.thumbnailUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={track.thumbnailUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              <div className="art-fallback flex h-full w-full items-center justify-center">
                <Music2 size={18} className="text-white/25" />
              </div>
            )}
            {isPlaying && (
              <div className="absolute inset-0 flex items-end justify-center gap-[2px] bg-black/35 pb-1.5">
                {[0, 1, 2].map((i) => (
                  <span
                    key={i}
                    className="pulse-live w-[2.5px] rounded-full bg-[var(--brand)]"
                    style={{ height: 9, animationDelay: `${i * 0.15}s` }}
                  />
                ))}
              </div>
            )}
          </div>
          <div className="min-w-0">
            <div className="truncate text-[14px] font-semibold tracking-tight">{track.title}</div>
            <div className="truncate text-[12.5px] text-[var(--ink-soft)]">
              {error ? <span className="text-red-400">{error}</span> : track.artist}
            </div>
          </div>
        </div>

        {/* Contrôles centraux */}
        <div className="flex flex-col items-center gap-1.5">
          <div className="flex items-center gap-4">
            <button
              onClick={toggleShuffle}
              title="Lecture aléatoire"
              className={`transition-colors ${shuffle ? "text-[var(--brand)]" : "text-[var(--ink-dim)] hover:text-[var(--ink)]"}`}
            >
              <Shuffle size={16} />
            </button>
            <button
              onClick={previous}
              disabled={!hasPrevious}
              title="Précédent"
              className="text-[var(--ink-soft)] transition-colors hover:text-[var(--ink)] disabled:opacity-30"
            >
              <SkipBack size={18} fill="currentColor" />
            </button>
            <button
              onClick={togglePlay}
              disabled={isLoading}
              className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-white text-black shadow-[var(--shadow-card)] transition-transform active:scale-90 disabled:opacity-60"
            >
              {isLoading ? (
                <Loader2 size={16} className="animate-spin" />
              ) : isPlaying ? (
                <Pause size={16} fill="currentColor" />
              ) : (
                <Play size={16} fill="currentColor" className="ml-0.5" />
              )}
            </button>
            <button
              onClick={next}
              disabled={!hasNext}
              title="Suivant"
              className="text-[var(--ink-soft)] transition-colors hover:text-[var(--ink)] disabled:opacity-30"
            >
              <SkipForward size={18} fill="currentColor" />
            </button>
            <button
              onClick={cycleRepeat}
              title={repeat === "one" ? "Répéter le morceau" : repeat === "all" ? "Répéter la file" : "Répétition désactivée"}
              className={`transition-colors ${repeat !== "off" ? "text-[var(--brand)]" : "text-[var(--ink-dim)] hover:text-[var(--ink)]"}`}
            >
              {repeat === "one" ? <Repeat1 size={16} /> : <Repeat size={16} />}
            </button>
          </div>
          <div className="flex w-full max-w-md items-center gap-2 font-mono text-[11px] tabular-nums text-[var(--ink-dim)]">
            <span>{fmt(progress)}</span>
            <span className="flex-1" />
            <span>{fmt(duration)}</span>
          </div>
        </div>

        {/* Volume */}
        <div className="flex items-center justify-end gap-2">
          {track.recordingId && (
            <OfflineDownloadButton
              track={{ recordingId: track.recordingId, title: track.title, artist: track.artist, album: track.album }}
              size={15}
            />
          )}
          <VolumeIcon size={16} className="text-[var(--ink-dim)]" />
          <input
            type="range"
            min={0}
            max={1}
            step={0.01}
            value={volume}
            onChange={(e) => setVolume(Number(e.target.value))}
            className="h-1 w-24 cursor-pointer accent-[var(--brand)]"
          />
        </div>
      </div>
    </div>
  );
}
