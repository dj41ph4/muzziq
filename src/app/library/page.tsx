"use client";

import useSWR from "swr";
import { useState } from "react";
import type { MediaFile } from "@/lib/library/mediaFilesStore";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

export default function LibraryPage() {
  const { data, mutate } = useSWR<{ files: MediaFile[] }>("/api/library", fetcher);
  const [scanning, setScanning] = useState(false);
  const [scanResult, setScanResult] = useState<string | null>(null);
  const [nowPlaying, setNowPlaying] = useState<MediaFile | null>(null);

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

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-6 py-10">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Bibliothèque locale</h1>
        <button
          onClick={runScan}
          disabled={scanning}
          className="rounded-xl bg-[var(--brand)] px-4 py-2 font-bold text-black disabled:opacity-50"
        >
          {scanning ? "Scan en cours…" : "Scanner"}
        </button>
      </div>

      {scanResult && <p className="text-sm text-[var(--ink-soft)]">{scanResult}</p>}

      {nowPlaying && (
        <div className="rounded-xl border border-white/10 bg-[var(--panel)] p-3">
          <div className="mb-2 text-sm font-semibold">
            {nowPlaying.title} — {nowPlaying.artist}
          </div>
          {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
          <audio controls autoPlay src={`/api/stream/${nowPlaying.id}`} className="w-full" />
        </div>
      )}

      <ul className="flex flex-col gap-2">
        {data?.files?.map((f) => (
          <li
            key={f.id}
            onClick={() => setNowPlaying(f)}
            className="flex cursor-pointer items-center gap-3 rounded-xl border border-white/10 bg-[var(--panel)] p-3 hover:border-[var(--brand)]/50"
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
        <p className="text-[var(--ink-dim)]">
          Aucun fichier. Configure un dossier musique dans les settings puis lance un scan.
        </p>
      )}
    </main>
  );
}
