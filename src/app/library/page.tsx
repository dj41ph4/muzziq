"use client";

import useSWR from "swr";
import { useState } from "react";
import type { MediaFile } from "@/lib/library/mediaFilesStore";
import { usePlayer } from "@/components/PlayerContext";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface LibraryItemView {
  id: string;
  recordingId: string;
  addPolicy: string;
  addedAt: string;
  recording?: { title: string; artist: string; album?: string };
}

interface HistoryEventView {
  id: string;
  type: string;
  at: string;
  recording?: { title: string; artist: string };
}

export default function LibraryPage() {
  const { data, mutate } = useSWR<{ files: MediaFile[] }>("/api/library", fetcher);
  const { data: libItems, mutate: mutateItems } = useSWR<{ items: LibraryItemView[] }>("/api/library/items", fetcher);
  const { data: history } = useSWR<{ events: HistoryEventView[] }>("/api/events", fetcher, { refreshInterval: 10000 });

  const [scanning, setScanning] = useState(false);
  const [scanResult, setScanResult] = useState<string | null>(null);
  const { play, track: nowPlaying } = usePlayer();

  async function runScan() {
    setScanning(true);
    setScanResult(null);
    try {
      const res = await fetch("/api/library/scan", { method: "POST" });
      const summary = await res.json();
      if (!res.ok) {
        setScanResult(summary.error ?? "Échec du scan");
      } else {
        setScanResult(
          `${summary.filesFound} fichiers trouvés, ${summary.added} ajoutés/mis à jour, ${summary.removed} retirés, ${summary.failed} échecs (${summary.durationMs} ms)`
        );
        mutate();
      }
    } finally {
      setScanning(false);
    }
  }

  function playLocal(f: MediaFile) {
    play({ kind: "local", id: f.id, title: f.title, artist: f.artist, album: f.album, durationSeconds: f.durationSeconds });
  }

  async function removeItem(id: string) {
    await fetch(`/api/library/items/${id}`, { method: "DELETE" });
    mutateItems();
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-8 px-6 py-10">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Bibliothèque</h1>
        <button
          onClick={runScan}
          disabled={scanning}
          className="rounded-xl bg-[var(--brand)] px-4 py-2 font-bold text-black disabled:opacity-50"
        >
          {scanning ? "Scan en cours…" : "Scanner le disque"}
        </button>
      </div>

      {scanResult && <p className="text-sm text-[var(--ink-soft)]">{scanResult}</p>}

      <section>
        <h2 className="mb-2 text-sm font-bold uppercase text-[var(--ink-dim)]">Fichiers locaux ({data?.files?.length ?? 0})</h2>
        <ul className="flex flex-col gap-2">
          {data?.files?.map((f) => (
            <li
              key={f.id}
              onClick={() => playLocal(f)}
              className={`flex cursor-pointer items-center gap-3 rounded-xl border p-3 hover:border-[var(--brand)]/50 ${
                nowPlaying?.kind === "local" && nowPlaying.id === f.id ? "border-[var(--brand)]/60 bg-[var(--brand)]/5" : "border-white/10 bg-[var(--panel)]"
              }`}
            >
              <div className="min-w-0 flex-1">
                <div className="truncate font-semibold">{f.title}</div>
                <div className="truncate text-sm text-[var(--ink-soft)]">
                  {f.artist}
                  {f.album ? ` • ${f.album}` : ""}
                </div>
              </div>
              <span className="rounded-full border border-white/15 px-2 py-0.5 text-[10px] font-bold uppercase text-[var(--ink-dim)]">
                {f.container}
                {f.bitsPerSample ? ` ${f.bitsPerSample}bit` : ""}
                {f.sampleRate ? ` ${(f.sampleRate / 1000).toFixed(1)}kHz` : ""}
              </span>
            </li>
          ))}
        </ul>
        {data && data.files.length === 0 && (
          <p className="text-sm text-[var(--ink-dim)]">Aucun fichier. Configure un dossier musique puis lance un scan.</p>
        )}
      </section>

      <section>
        <h2 className="mb-2 text-sm font-bold uppercase text-[var(--ink-dim)]">
          Ajoutés depuis la recherche ({libItems?.items?.length ?? 0})
        </h2>
        <ul className="flex flex-col gap-2">
          {libItems?.items?.map((item) => (
            <li key={item.id} className="flex items-center gap-3 rounded-xl border border-white/10 bg-[var(--panel)] p-3">
              <div className="min-w-0 flex-1">
                <div className="truncate font-semibold">{item.recording?.title ?? "?"}</div>
                <div className="truncate text-sm text-[var(--ink-soft)]">{item.recording?.artist}</div>
              </div>
              <span className="rounded-full border border-white/15 px-2 py-0.5 text-[10px] font-bold text-[var(--ink-dim)]">
                {item.addPolicy}
              </span>
              <button onClick={() => removeItem(item.id)} className="text-xs text-[var(--ink-dim)] hover:text-red-400">
                Retirer
              </button>
            </li>
          ))}
        </ul>
        {libItems && libItems.items.length === 0 && (
          <p className="text-sm text-[var(--ink-dim)]">Rien pour l&apos;instant — ajoute un morceau depuis la recherche.</p>
        )}
      </section>

      <section>
        <h2 className="mb-2 text-sm font-bold uppercase text-[var(--ink-dim)]">Historique récent</h2>
        <ul className="flex flex-col gap-1">
          {history?.events?.slice(0, 10).map((e) => (
            <li key={e.id} className="flex items-center justify-between text-sm text-[var(--ink-soft)]">
              <span>
                {e.recording?.title} — {e.recording?.artist}
              </span>
              <span className="text-[var(--ink-dim)]">{new Date(e.at).toLocaleTimeString()}</span>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
