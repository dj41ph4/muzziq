"use client";

import useSWR from "swr";
import { useState } from "react";
import Link from "next/link";
import { RefreshCw, Music2, X, Clock, HardDrive, ListMusic, Download, DownloadCloud, Sparkles, Settings } from "lucide-react";
import type { MediaFile } from "@/lib/library/mediaFilesStore";
import { usePlayer, type PlayableTrack } from "@/components/PlayerContext";
import { TopBar } from "@/components/TopBar";
import { OfflineDownloadButton } from "@/components/OfflineDownloadButton";

const SHORTCUTS = [
  { href: "/playlists", label: "Playlists", Icon: ListMusic },
  { href: "/downloads", label: "Téléchargements", Icon: Download },
  { href: "/offline", label: "Hors ligne", Icon: DownloadCloud },
  { href: "/assistant", label: "Assistant", Icon: Sparkles },
  { href: "/settings", label: "Réglages", Icon: Settings },
];

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

function TrackRow({
  title,
  artist,
  album,
  active,
  onClick,
  right,
}: {
  title: string;
  artist: string;
  album?: string;
  active?: boolean;
  onClick?: () => void;
  right?: React.ReactNode;
}) {
  return (
    <li
      onClick={onClick}
      className={`flex items-center gap-3 rounded-xl p-2.5 transition-colors ${
        onClick ? "cursor-pointer hover:bg-white/[0.04]" : ""
      } ${active ? "bg-[var(--brand)]/[0.07]" : ""}`}
    >
      <div className="art-fallback flex h-11 w-11 flex-shrink-0 items-center justify-center overflow-hidden rounded-lg shadow-[var(--shadow-card)]">
        <Music2 size={16} className="text-white/25" />
      </div>
      <div className="min-w-0 flex-1">
        <div className={`truncate text-[14px] font-semibold tracking-tight ${active ? "text-[var(--brand)]" : ""}`}>{title}</div>
        <div className="truncate text-[13px] text-[var(--ink-soft)]">
          {artist}
          {album ? ` • ${album}` : ""}
        </div>
      </div>
      {right}
    </li>
  );
}

function TrackCard({
  title,
  artist,
  tech,
  active,
  onClick,
}: {
  title: string;
  artist: string;
  tech?: string;
  active?: boolean;
  onClick?: () => void;
}) {
  return (
    <div onClick={onClick} className="group cursor-pointer">
      <div className="art-fallback relative flex aspect-square w-full items-center justify-center overflow-hidden rounded-xl shadow-[var(--shadow-card)]">
        <Music2 size={26} className="text-white/20" />
        <div className="absolute inset-0 flex items-center justify-center bg-black/45 opacity-0 transition-opacity group-hover:opacity-100">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--brand)] text-black shadow-[var(--shadow-card)]">
            <Music2 size={16} />
          </div>
        </div>
        {tech && (
          <span className="absolute bottom-1.5 right-1.5 rounded-full bg-black/60 px-1.5 py-0.5 text-[9px] font-bold uppercase text-white/70 opacity-0 backdrop-blur-sm transition-opacity group-hover:opacity-100">
            {tech}
          </span>
        )}
      </div>
      <div className={`mt-2 truncate text-[13px] font-semibold tracking-tight ${active ? "text-[var(--brand)]" : ""}`}>{title}</div>
      <div className="truncate text-[12px] text-[var(--ink-soft)]">{artist}</div>
    </div>
  );
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
    const queue: PlayableTrack[] = (data?.files ?? []).map((x) => ({
      kind: "local",
      id: x.id,
      title: x.title,
      artist: x.artist,
      album: x.album,
      durationSeconds: x.durationSeconds,
    }));
    play({ kind: "local", id: f.id, title: f.title, artist: f.artist, album: f.album, durationSeconds: f.durationSeconds }, queue);
  }

  async function removeItem(id: string) {
    await fetch(`/api/library/items/${id}`, { method: "DELETE" });
    mutateItems();
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-6xl flex-col gap-9 px-5 pt-8 sm:px-8">
      <div className="flex items-center justify-between">
        <TopBar title="Bibliothèque" />
        <button
          onClick={runScan}
          disabled={scanning}
          className="brand-gradient flex items-center gap-2 rounded-full px-4 py-2.5 text-[13px] font-bold text-black shadow-[0_4px_16px_-4px_var(--brand-glow)] transition-transform active:scale-95 disabled:opacity-60"
        >
          <RefreshCw size={14} className={scanning ? "animate-spin" : ""} />
          {scanning ? "Scan…" : "Scanner"}
        </button>
      </div>

      {scanResult && (
        <p className="glass float-in -mt-4 rounded-xl px-4 py-2.5 text-[13px] text-[var(--ink-soft)]">{scanResult}</p>
      )}

      <div className="float-in -mx-5 flex gap-3 overflow-x-auto px-5 sm:-mx-8 sm:px-8" style={{ scrollbarWidth: "none" }}>
        {SHORTCUTS.map(({ href, label, Icon }) => (
          <Link
            key={href}
            href={href}
            className="glass flex flex-shrink-0 items-center gap-2 rounded-full px-4 py-2.5 text-[13px] font-semibold text-[var(--ink-soft)] transition-colors hover:text-[var(--ink)]"
          >
            <Icon size={15} />
            {label}
          </Link>
        ))}
      </div>

      <section className="float-in">
        <div className="mb-3 flex items-center gap-2 text-[var(--ink-dim)]">
          <HardDrive size={14} />
          <h2 className="text-[13px] font-bold uppercase tracking-wide">Fichiers locaux ({data?.files?.length ?? 0})</h2>
        </div>
        <div className="grid grid-cols-2 gap-x-4 gap-y-5 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
          {data?.files?.map((f) => (
            <TrackCard
              key={f.id}
              title={f.title}
              artist={f.artist}
              tech={`${f.container}${f.bitsPerSample ? ` ${f.bitsPerSample}b` : ""}${f.sampleRate ? ` ${(f.sampleRate / 1000).toFixed(1)}k` : ""}`}
              active={nowPlaying?.kind === "local" && nowPlaying.id === f.id}
              onClick={() => playLocal(f)}
            />
          ))}
        </div>
        {data && data.files.length === 0 && (
          <p className="glass rounded-xl px-4 py-6 text-center text-sm text-[var(--ink-dim)]">
            Aucun fichier. Configure un dossier musique puis lance un scan.
          </p>
        )}
      </section>

      <div className="mx-auto flex w-full max-w-2xl flex-col gap-9">
        <section className="float-in" style={{ animationDelay: "60ms" }}>
          <div className="mb-3 flex items-center gap-2 text-[var(--ink-dim)]">
            <Music2 size={14} />
            <h2 className="text-[13px] font-bold uppercase tracking-wide">Ajoutés depuis la recherche ({libItems?.items?.length ?? 0})</h2>
          </div>
          <ul className="flex flex-col gap-0.5">
            {libItems?.items?.map((item) => (
              <TrackRow
                key={item.id}
                title={item.recording?.title ?? "?"}
                artist={item.recording?.artist ?? ""}
                right={
                  <div className="flex items-center gap-2">
                    <span className="hidden rounded-full border border-white/10 px-2 py-0.5 text-[10px] font-bold text-[var(--ink-dim)] sm:inline">
                      {item.addPolicy}
                    </span>
                    {item.recording && (
                      <OfflineDownloadButton
                        track={{ recordingId: item.recordingId, title: item.recording.title, artist: item.recording.artist, album: item.recording.album }}
                        size={14}
                      />
                    )}
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        removeItem(item.id);
                      }}
                      className="flex h-7 w-7 items-center justify-center rounded-full text-[var(--ink-dim)] transition-colors hover:bg-red-500/10 hover:text-red-400"
                    >
                      <X size={14} />
                    </button>
                  </div>
                }
              />
            ))}
          </ul>
          {libItems && libItems.items.length === 0 && (
            <p className="glass rounded-xl px-4 py-6 text-center text-sm text-[var(--ink-dim)]">
              Rien pour l&apos;instant — ajoute un morceau depuis la recherche.
            </p>
          )}
        </section>

        <section className="float-in pb-4" style={{ animationDelay: "120ms" }}>
          <div className="mb-3 flex items-center gap-2 text-[var(--ink-dim)]">
            <Clock size={14} />
            <h2 className="text-[13px] font-bold uppercase tracking-wide">Historique récent</h2>
          </div>
          <ul className="flex flex-col gap-1.5">
            {history?.events?.slice(0, 10).map((e) => (
              <li key={e.id} className="flex items-center justify-between px-2.5 text-[13px] text-[var(--ink-soft)]">
                <span className="truncate">
                  {e.recording?.title} — {e.recording?.artist}
                </span>
                <span className="flex-shrink-0 font-mono text-[11px] text-[var(--ink-dim)]">
                  {new Date(e.at).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })}
                </span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </main>
  );
}
