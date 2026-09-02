"use client";

import { useState } from "react";
import useSWR from "swr";
import { RefreshCw, CheckCircle2, XCircle } from "lucide-react";
import { TopBar } from "@/components/TopBar";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface SettingsView {
  serverName: string;
  musicDir: string | null;
}

export default function LibrarySettingsPage() {
  const { data, mutate } = useSWR<SettingsView>("/api/settings", fetcher);
  const [musicDir, setMusicDir] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [scanResult, setScanResult] = useState<{ ok: boolean; detail: string } | null>(null);

  const value = musicDir ?? data?.musicDir ?? "";

  async function save() {
    setSaved(false);
    const res = await fetch("/api/settings", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ musicDir: value.trim() || null }),
    });
    if (res.ok) {
      setSaved(true);
      mutate();
    }
  }

  async function scan() {
    setScanning(true);
    setScanResult(null);
    const res = await fetch("/api/library/scan", { method: "POST" });
    const body = await res.json();
    setScanResult(
      res.ok
        ? { ok: true, detail: `${body.filesFound} fichiers trouvés, ${body.added} ajoutés/mis à jour, ${body.failed} échecs` }
        : { ok: false, detail: body.error }
    );
    setScanning(false);
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <TopBar title="Bibliothèque" />
      <p className="float-in -mt-2 text-sm text-[var(--ink-soft)]">
        Chemin vu <strong>depuis l&apos;intérieur du conteneur/process MuzziQ</strong> — pas le chemin sur ton NAS.
        Si tu as monté ton dossier NAS sur <code>/music</code> dans Docker (comme dans le
        docker-compose fourni), la valeur ici est simplement <code>/music</code>, jamais le chemin
        du NAS lui-même.
      </p>

      <div className="glass float-in flex flex-col gap-2 rounded-2xl p-4">
        <label className="text-xs font-semibold text-[var(--ink-dim)]">Dossier musique</label>
        <input
          value={value}
          onChange={(e) => {
            setMusicDir(e.target.value);
            setSaved(false);
          }}
          placeholder="/music"
          className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 font-mono text-sm outline-none focus:border-[var(--brand)]"
        />
        <button
          onClick={save}
          className="brand-gradient mt-1 rounded-full px-4 py-2.5 text-[13px] font-bold text-black transition-transform active:scale-95"
        >
          Enregistrer
        </button>
        {saved && <p className="text-xs text-[var(--brand)]">Enregistré.</p>}
      </div>

      <div className="glass float-in flex flex-col gap-2 rounded-2xl p-4">
        <button
          onClick={scan}
          disabled={scanning || !data?.musicDir}
          className="flex items-center justify-center gap-2 rounded-full border border-white/15 px-4 py-2.5 text-[13px] font-bold text-[var(--ink-soft)] transition-colors hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-40"
        >
          <RefreshCw size={14} className={scanning ? "animate-spin" : ""} />
          {scanning ? "Scan en cours…" : "Lancer un scan maintenant"}
        </button>
        {!data?.musicDir && (
          <p className="text-xs text-[var(--ink-dim)]">Enregistre d&apos;abord un dossier ci-dessus.</p>
        )}
        {scanResult && (
          <div className={`flex items-center gap-1.5 text-xs ${scanResult.ok ? "text-[var(--brand)]" : "text-red-400"}`}>
            {scanResult.ok ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
            {scanResult.detail}
          </div>
        )}
      </div>
    </main>
  );
}
