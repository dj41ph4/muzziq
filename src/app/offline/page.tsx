"use client";

import useSWR from "swr";
import { Check, HardDrive, Loader2, Music2, RotateCw, Trash2, X } from "lucide-react";
import { TopBar } from "@/components/TopBar";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface OfflineDownloadView {
  id: string;
  recordingId: string;
  title: string;
  artist: string;
  album?: string;
  state: "QUEUED" | "DOWNLOADING" | "COMPLETED" | "FAILED";
  sourceKind: "local" | "provider" | null;
  sizeBytes: number | null;
  error: string | null;
  updatedAt: string;
}

function fmtBytes(b: number | null): string {
  if (!b) return "—";
  const units = ["o", "Ko", "Mo", "Go"];
  const i = Math.min(units.length - 1, Math.floor(Math.log(b) / Math.log(1024)));
  return `${(b / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0)} ${units[i]}`;
}

function StateBadge({ state }: { state: OfflineDownloadView["state"] }) {
  if (state === "COMPLETED") {
    return (
      <span className="flex items-center gap-1 rounded-full border border-[var(--brand)]/30 bg-[var(--brand)]/12 px-2 py-0.5 text-[10px] font-bold text-[var(--brand)]">
        <Check size={11} /> Disponible hors ligne
      </span>
    );
  }
  if (state === "FAILED") {
    return (
      <span className="flex items-center gap-1 rounded-full border border-red-400/30 bg-red-400/10 px-2 py-0.5 text-[10px] font-bold text-red-400">
        <X size={11} /> Échec
      </span>
    );
  }
  return (
    <span className="flex items-center gap-1 rounded-full border border-white/10 px-2 py-0.5 text-[10px] font-bold uppercase text-[var(--ink-dim)]">
      <Loader2 size={11} className="animate-spin" /> {state === "DOWNLOADING" ? "Téléchargement…" : "En attente…"}
    </span>
  );
}

/**
 * Écran "Hors ligne" — distinct de /downloads (acquisition torrent d'albums
 * FLAC, sujet complètement différent). Liste les morceaux réellement
 * téléchargés pour l'écoute hors ligne (peu importe la source d'origine :
 * fichier local déjà présent, ou flux provider vraiment fetché sur disque).
 */
export default function OfflinePage() {
  const { data, mutate } = useSWR<{ downloads: OfflineDownloadView[] }>("/api/offline", fetcher, { refreshInterval: 3000 });

  async function retry(d: OfflineDownloadView) {
    await fetch("/api/offline", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ recordingId: d.recordingId }),
    });
    mutate();
  }

  async function remove(id: string) {
    await fetch(`/api/offline/${id}`, { method: "DELETE" });
    mutate();
  }

  const downloads = data?.downloads ?? [];

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <TopBar title="Hors ligne" />
      <p className="-mt-4 text-[13px] text-[var(--ink-soft)]">
        Morceaux téléchargés pour être écoutés sans connexion — distinct des téléchargements d&apos;albums (torrent).
      </p>

      <ul className="flex flex-col gap-2">
        {downloads.map((d) => (
          <li key={d.id} className="glass float-in flex items-center gap-3 rounded-xl p-3">
            <div className="art-fallback flex h-11 w-11 flex-shrink-0 items-center justify-center overflow-hidden rounded-lg">
              <Music2 size={16} className="text-white/25" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-[14px] font-semibold tracking-tight">{d.title}</div>
              <div className="truncate text-[13px] text-[var(--ink-soft)]">
                {d.artist}
                {d.album ? ` • ${d.album}` : ""}
              </div>
              <div className="mt-1 flex flex-wrap items-center gap-2">
                <StateBadge state={d.state} />
                {d.state === "COMPLETED" && (
                  <span className="flex items-center gap-1 text-[11px] text-[var(--ink-dim)]">
                    <HardDrive size={11} /> {fmtBytes(d.sizeBytes)}
                    {d.sourceKind === "local" ? " · déjà local" : ""}
                  </span>
                )}
                {d.state === "FAILED" && d.error && <span className="truncate text-[11px] text-red-400/80">{d.error}</span>}
              </div>
            </div>
            {d.state === "FAILED" && (
              <button
                onClick={() => retry(d)}
                title="Réessayer"
                className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-[var(--ink-dim)] transition-colors hover:bg-white/10 hover:text-[var(--ink)]"
              >
                <RotateCw size={14} />
              </button>
            )}
            <button
              onClick={() => remove(d.id)}
              title="Retirer"
              className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-[var(--ink-dim)] transition-colors hover:bg-red-500/10 hover:text-red-400"
            >
              <Trash2 size={14} />
            </button>
          </li>
        ))}
      </ul>

      {data && downloads.length === 0 && (
        <p className="glass rounded-xl px-4 py-6 text-center text-sm text-[var(--ink-dim)]">
          Aucun téléchargement hors ligne pour l&apos;instant — utilise le bouton de téléchargement sur un morceau (playlist, bibliothèque, lecteur).
        </p>
      )}
    </main>
  );
}
