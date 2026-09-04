"use client";

import { useState } from "react";
import useSWR from "swr";
import { Heart, Loader2 } from "lucide-react";

const fetcher = (url: string) => fetch(url).then((response) => response.json());

export interface FavoriteTrackInfo {
  recordingId?: string;
  provider?: string;
  providerTrackId?: string;
  title: string;
  artist: string;
  album?: string;
  durationSeconds?: number;
  thumbnailUrl?: string;
}

interface FavoriteEntry {
  recordingId: string;
}

export function FavoriteButton({ track, size = 16 }: { track: FavoriteTrackInfo; size?: number }) {
  const { data, mutate } = useSWR<{ favorites: FavoriteEntry[] }>("/api/favorites", fetcher, { refreshInterval: 10000 });
  const [pending, setPending] = useState(false);
  const [resolvedRecordingId, setResolvedRecordingId] = useState<string | undefined>(track.recordingId);
  const effectiveId = resolvedRecordingId ?? track.recordingId;
  const liked = !!effectiveId && !!data?.favorites?.some((favorite) => favorite.recordingId === effectiveId);

  async function toggle(e: React.MouseEvent) {
    e.stopPropagation();
    if (pending) return;
    setPending(true);
    try {
      if (liked && effectiveId) {
        await fetch(`/api/favorites?recordingId=${encodeURIComponent(effectiveId)}`, { method: "DELETE" });
      } else {
        const response = await fetch("/api/favorites", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(track),
        });
        const body = await response.json().catch(() => ({}));
        if (response.ok && body.recordingId) setResolvedRecordingId(body.recordingId);
      }
      await mutate();
    } finally {
      setPending(false);
    }
  }

  return (
    <button
      onClick={toggle}
      disabled={pending}
      title={liked ? "Retirer des titres likés" : "Ajouter aux titres likés"}
      className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full transition-colors disabled:opacity-60 ${liked ? "text-[var(--brand)]" : "text-[var(--ink-dim)] hover:bg-white/10 hover:text-[var(--ink)]"}`}
    >
      {pending ? <Loader2 size={size} className="animate-spin" /> : <Heart size={size} fill={liked ? "currentColor" : "none"} />}
    </button>
  );
}
