"use client";

import { useState } from "react";
import useSWR from "swr";
import { Download, Pause, Play, X } from "lucide-react";
import { TopBar } from "@/components/TopBar";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface TorrentSummary {
  infoHash: string;
  name: string;
  length: number;
  downloaded: number;
  progress: number;
  downloadSpeed: number;
  numPeers: number;
  state: string;
}

function fmtBytes(b: number): string {
  if (!b) return "0 o";
  const units = ["o", "Ko", "Mo", "Go"];
  const i = Math.min(units.length - 1, Math.floor(Math.log(b) / Math.log(1024)));
  return `${(b / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0)} ${units[i]}`;
}

export default function DownloadsPage() {
  const { data, mutate } = useSWR<{ torrents: TorrentSummary[] }>("/api/downloads", fetcher, { refreshInterval: 2000 });
  const [magnet, setMagnet] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  async function add() {
    if (!magnet.trim()) return;
    setAdding(true);
    setError(null);
    try {
      const res = await fetch("/api/downloads", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ magnetOrUrl: magnet.trim() }),
      });
      const body = await res.json();
      if (!res.ok) setError(body.error);
      else {
        setMagnet("");
        mutate();
      }
    } finally {
      setAdding(false);
    }
  }

  async function toggle(infoHash: string, action: "pause" | "resume") {
    await fetch(`/api/downloads/${infoHash}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action }),
    });
    mutate();
  }

  async function remove(infoHash: string) {
    await fetch(`/api/downloads/${infoHash}?deleteData=true`, { method: "DELETE" });
    mutate();
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <TopBar title="Téléchargements" />

      <div className="glass float-in flex flex-col gap-2 rounded-2xl p-4">
        <input
          value={magnet}
          onChange={(e) => setMagnet(e.target.value)}
          placeholder="Lien magnet ou URL .torrent…"
          className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-[var(--brand)]"
        />
        <button
          onClick={add}
          disabled={adding || !magnet.trim()}
          className="brand-gradient mt-1 flex items-center justify-center gap-2 rounded-full px-4 py-2.5 text-[13px] font-bold text-black transition-transform active:scale-95 disabled:opacity-50"
        >
          <Download size={15} />
          {adding ? "Résolution…" : "Télécharger"}
        </button>
        {error && <p className="text-xs text-red-400">{error}</p>}
      </div>

      <ul className="flex flex-col gap-2">
        {data?.torrents?.map((t) => (
          <li key={t.infoHash} className="glass flex flex-col gap-2 rounded-xl p-3">
            <div className="flex items-center gap-3">
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-semibold">{t.name}</div>
                <div className="text-xs text-[var(--ink-dim)]">
                  {fmtBytes(t.downloaded)} / {fmtBytes(t.length)} · {t.numPeers} pairs · {fmtBytes(t.downloadSpeed)}/s
                </div>
              </div>
              <span className="rounded-full border border-white/10 px-2 py-0.5 text-[10px] font-bold uppercase text-[var(--ink-dim)]">
                {t.state}
              </span>
              {t.state === "paused" ? (
                <button onClick={() => toggle(t.infoHash, "resume")} className="flex h-8 w-8 items-center justify-center rounded-full hover:bg-white/10">
                  <Play size={14} />
                </button>
              ) : (
                <button onClick={() => toggle(t.infoHash, "pause")} className="flex h-8 w-8 items-center justify-center rounded-full hover:bg-white/10">
                  <Pause size={14} />
                </button>
              )}
              <button onClick={() => remove(t.infoHash)} className="flex h-8 w-8 items-center justify-center rounded-full text-[var(--ink-dim)] hover:bg-red-500/10 hover:text-red-400">
                <X size={14} />
              </button>
            </div>
            <div className="h-1 overflow-hidden rounded-full bg-white/[0.06]">
              <div className="h-full bg-[var(--brand)] transition-[width]" style={{ width: `${Math.round(t.progress * 100)}%` }} />
            </div>
          </li>
        ))}
      </ul>
      {data && data.torrents.length === 0 && (
        <p className="glass rounded-xl px-4 py-6 text-center text-sm text-[var(--ink-dim)]">Aucun téléchargement en cours.</p>
      )}
    </main>
  );
}
