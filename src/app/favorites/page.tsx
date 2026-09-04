"use client";

import Link from "next/link";
import useSWR from "swr";
import { Heart, Play, Search, Trash2 } from "lucide-react";
import { TopBar } from "@/components/TopBar";
import { OfflineDownloadButton } from "@/components/OfflineDownloadButton";
import { usePlayer, type PlayableTrack } from "@/components/PlayerContext";

const fetcher = (url: string) => fetch(url).then((response) => response.json());

interface FavoriteView {
  recordingId: string;
  recording?: { id: string; title: string; artist: string; album?: string; thumbnailUrl?: string; durationSeconds?: number };
  provider?: string;
  providerTrackId?: string;
}

export default function FavoritesPage() {
  const { data, mutate } = useSWR<{ favorites: FavoriteView[] }>("/api/favorites", fetcher);
  const { play, track: nowPlaying } = usePlayer();
  const favorites = data?.favorites?.filter((favorite) => favorite.recording) ?? [];

  function toPlayable(favorite: FavoriteView): PlayableTrack | null {
    const recording = favorite.recording;
    if (!recording) return null;
    return { kind: "provider", id: favorite.providerTrackId ?? recording.id, recordingId: recording.id, title: recording.title, artist: recording.artist, album: recording.album, thumbnailUrl: recording.thumbnailUrl, durationSeconds: recording.durationSeconds };
  }

  async function playFavorite(favorite: FavoriteView) {
    const track = toPlayable(favorite);
    if (!track) return;
    const queue = favorites.map(toPlayable).filter((item): item is PlayableTrack => item !== null);
    const resolved = await fetch(`/api/recordings/${favorite.recordingId}/resolve`);
    if (resolved.ok) {
      const source = (await resolved.json()) as { kind: PlayableTrack["kind"]; id: string };
      play({ ...track, kind: source.kind, id: source.id }, queue);
    }
  }

  async function remove(recordingId: string) {
    await fetch(`/api/favorites?recordingId=${encodeURIComponent(recordingId)}`, { method: "DELETE" });
    mutate();
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <TopBar title="Titres likés" />
      <div className="glass float-in flex items-center gap-4 rounded-2xl p-4">
        <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-[var(--brand)] text-black shadow-[var(--shadow-card)]">
          <Heart size={25} fill="currentColor" />
        </div>
        <div>
          <div className="text-sm font-bold">Ta sélection personnelle</div>
          <div className="text-xs text-[var(--ink-soft)]">{favorites.length} titre{favorites.length === 1 ? "" : "s"} aimé{favorites.length === 1 ? "" : "s"}</div>
        </div>
      </div>

      <ul className="flex flex-col gap-1">
        {favorites.map((favorite) => {
          const recording = favorite.recording!;
          const active = nowPlaying?.recordingId === recording.id || nowPlaying?.id === recording.id;
          return (
            <li key={favorite.recordingId} className={`group flex items-center gap-3 rounded-xl p-2.5 transition-colors hover:bg-white/[0.04] ${active ? "bg-[var(--brand)]/[0.07]" : ""}`}>
              <button onClick={() => playFavorite(favorite)} className="relative flex h-11 w-11 flex-shrink-0 items-center justify-center overflow-hidden rounded-lg bg-white/[0.06]" title="Lire">
                {recording.thumbnailUrl ? <img src={recording.thumbnailUrl} alt="" className="h-full w-full object-cover" /> : <Play size={16} fill="currentColor" />}
              </button>
              <button onClick={() => playFavorite(favorite)} className="min-w-0 flex-1 text-left">
                <div className={`truncate text-[14px] font-semibold ${active ? "text-[var(--brand)]" : ""}`}>{recording.title}</div>
                <div className="truncate text-[13px] text-[var(--ink-soft)]">{recording.artist}{recording.album ? ` • ${recording.album}` : ""}</div>
              </button>
              <OfflineDownloadButton track={{ recordingId: recording.id, provider: favorite.provider, providerTrackId: favorite.providerTrackId, title: recording.title, artist: recording.artist, album: recording.album, durationSeconds: recording.durationSeconds, thumbnailUrl: recording.thumbnailUrl }} size={15} />
              <button onClick={() => remove(recording.id)} title="Retirer des titres likés" className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-[var(--ink-dim)] hover:bg-red-500/10 hover:text-red-400">
                <Trash2 size={14} />
              </button>
            </li>
          );
        })}
      </ul>

      {data && favorites.length === 0 && (
        <div className="glass float-in flex flex-col items-center gap-3 rounded-2xl px-8 py-12 text-center">
          <Heart size={30} className="text-[var(--ink-dim)]" />
          <p className="max-w-xs text-sm text-[var(--ink-soft)]">Aucun titre liké pour l'instant. Utilise le cœur dans les résultats de recherche.</p>
          <Link href="/search" className="brand-gradient flex items-center gap-2 rounded-full px-4 py-2.5 text-[13px] font-bold text-black"><Search size={15} /> Rechercher de la musique</Link>
        </div>
      )}
    </main>
  );
}
