"use client";

import { useState } from "react";
import useSWR from "swr";
import { Check, Download, Loader2, X } from "lucide-react";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

export interface OfflineTrackInfo {
  recordingId: string;
  title: string;
  artist: string;
  album?: string;
}

interface OfflineDownloadEntry {
  id: string;
  recordingId: string;
  state: "QUEUED" | "DOWNLOADING" | "COMPLETED" | "FAILED";
  error: string | null;
}

/**
 * Bouton "Télécharger pour écouter hors ligne" (indépendant de la source
 * d'origine — local, provider — voir POST /api/offline). Partagé entre
 * playlist, bibliothèque et player plutôt que dupliqué par écran.
 */
export function OfflineDownloadButton({ track, size = 16 }: { track: OfflineTrackInfo; size?: number }) {
  const { data, mutate } = useSWR<{ downloads: OfflineDownloadEntry[] }>("/api/offline", fetcher, { refreshInterval: 5000 });
  const [pending, setPending] = useState(false);

  const entry = data?.downloads?.find((d) => d.recordingId === track.recordingId);
  const busy = pending || entry?.state === "QUEUED" || entry?.state === "DOWNLOADING";
  const completed = entry?.state === "COMPLETED";
  const failed = entry?.state === "FAILED";

  async function trigger(e: React.MouseEvent) {
    e.stopPropagation();
    if (busy || completed) return;
    setPending(true);
    try {
      await fetch("/api/offline", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ recordingId: track.recordingId }),
      });
      mutate();
    } finally {
      setPending(false);
    }
  }

  const title = completed
    ? "Disponible hors ligne"
    : failed
      ? `Échec du téléchargement — réessayer (${entry?.error ?? ""})`
      : busy
        ? "Téléchargement en cours…"
        : "Télécharger pour écouter hors ligne";

  return (
    <button
      onClick={trigger}
      disabled={busy || completed}
      title={title}
      className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full transition-colors disabled:cursor-default ${
        completed
          ? "text-[var(--brand)]"
          : failed
            ? "text-red-400 hover:bg-red-500/10"
            : "text-[var(--ink-dim)] hover:bg-white/10 hover:text-[var(--ink)]"
      }`}
    >
      {busy ? (
        <Loader2 size={size} className="animate-spin" />
      ) : completed ? (
        <Check size={size} />
      ) : failed ? (
        <X size={size} />
      ) : (
        <Download size={size} />
      )}
    </button>
  );
}
