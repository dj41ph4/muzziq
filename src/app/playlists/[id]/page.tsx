"use client";

import { use } from "react";
import useSWR from "swr";
import { X, Music2 } from "lucide-react";
import { usePlayer } from "@/components/PlayerContext";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface PlaylistItemView {
  id: string;
  recordingId: string;
  recording?: { title: string; artist: string; album?: string; thumbnailUrl?: string };
}

export default function PlaylistDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { data, mutate } = useSWR<{ name: string; items: PlaylistItemView[] }>(`/api/playlists/${id}`, fetcher);
  const { play, track: nowPlaying } = usePlayer();

  async function removeItem(itemId: string) {
    await fetch(`/api/playlists/${id}/items?itemId=${itemId}`, { method: "DELETE" });
    mutate();
  }

  async function playRecording(recordingId: string, r: NonNullable<PlaylistItemView["recording"]>) {
    const res = await fetch(`/api/recordings/${recordingId}/resolve`);
    if (!res.ok) return; // Playback Resolver n'a trouvé aucune source réelle — jamais fabriquer une lecture qui ne marchera pas.
    const resolved: { kind: "local" | "provider"; id: string } = await res.json();
    play({ kind: resolved.kind, id: resolved.id, title: r.title, artist: r.artist, album: r.album, thumbnailUrl: r.thumbnailUrl });
  }

  if (!data) return null;

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <h1 className="float-in text-3xl font-extrabold tracking-tight">{data.name}</h1>

      <ul className="flex flex-col gap-0.5">
        {data.items.map((item) => {
          const r = item.recording;
          if (!r) return null;
          const isCurrent = nowPlaying?.kind === "provider" && nowPlaying.title === r.title;
          return (
            <li
              key={item.id}
              className={`flex items-center gap-3 rounded-xl p-2.5 transition-colors hover:bg-white/[0.04] ${isCurrent ? "bg-[var(--brand)]/[0.07]" : ""}`}
            >
              <div
                onClick={() => playRecording(item.recordingId, r)}
                className="art-fallback flex h-11 w-11 flex-shrink-0 cursor-pointer items-center justify-center overflow-hidden rounded-lg"
              >
                {r.thumbnailUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={r.thumbnailUrl} alt="" className="h-full w-full object-cover" />
                ) : (
                  <Music2 size={16} className="text-white/25" />
                )}
              </div>
              <div className="min-w-0 flex-1">
                <div className={`truncate text-[14px] font-semibold ${isCurrent ? "text-[var(--brand)]" : ""}`}>{r.title}</div>
                <div className="truncate text-[13px] text-[var(--ink-soft)]">{r.artist}</div>
              </div>
              <button onClick={() => removeItem(item.id)} className="flex h-8 w-8 items-center justify-center rounded-full text-[var(--ink-dim)] hover:bg-red-500/10 hover:text-red-400">
                <X size={14} />
              </button>
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
