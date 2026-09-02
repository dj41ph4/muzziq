"use client";

import { useState } from "react";
import useSWR from "swr";
import { CheckCircle2, XCircle } from "lucide-react";
import { TopBar } from "@/components/TopBar";
import type { PlexConfig, PlexSyncPolicy } from "@/lib/integrations/plex/store";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

export default function PlexSettingsPage() {
  const { data, mutate } = useSWR<PlexConfig>("/api/integrations/plex", fetcher);
  const [testing, setTesting] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; detail: string } | null>(null);

  if (!data) return null;

  async function save(patch: Partial<PlexConfig>) {
    const res = await fetch("/api/integrations/plex", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(patch),
    });
    mutate(await res.json(), { revalidate: false });
  }

  async function test() {
    setTesting(true);
    setResult(null);
    const res = await fetch("/api/integrations/plex/test", { method: "POST" });
    setResult(await res.json());
    setTesting(false);
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <TopBar title="Plex" />
      <p className="float-in -mt-2 text-sm text-[var(--ink-soft)]">
        Intégration optionnelle — MuzziQ fonctionne entièrement sans Plex. Jamais une dépendance.
      </p>

      <div className="glass float-in flex flex-col gap-2 rounded-2xl p-4">
        <input
          defaultValue={data.serverUrl}
          onBlur={(e) => save({ serverUrl: e.target.value })}
          placeholder="http://192.168.1.10:32400"
          className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-[var(--brand)]"
        />
        <input
          type="password"
          defaultValue={data.token}
          onBlur={(e) => save({ token: e.target.value })}
          placeholder="X-Plex-Token"
          className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-[var(--brand)]"
        />
        <select
          value={data.syncPolicy}
          onChange={(e) => save({ syncPolicy: e.target.value as PlexSyncPolicy })}
          className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm"
        >
          <option value="OFF">Synchronisation désactivée</option>
          <option value="IMPORT_ONLY">Import uniquement (Plex → MuzziQ)</option>
          <option value="EXPORT_ONLY">Export uniquement (MuzziQ → Plex)</option>
          <option value="BIDIRECTIONAL">Bidirectionnelle</option>
        </select>
        <button
          onClick={test}
          disabled={testing}
          className="brand-gradient mt-1 rounded-full px-4 py-2.5 text-[13px] font-bold text-black transition-transform active:scale-95 disabled:opacity-50"
        >
          {testing ? "Test en cours…" : "Tester la connexion"}
        </button>
        {result && (
          <div className={`flex items-center gap-1.5 text-xs ${result.ok ? "text-[var(--brand)]" : "text-red-400"}`}>
            {result.ok ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
            {result.detail}
          </div>
        )}
      </div>
    </main>
  );
}
