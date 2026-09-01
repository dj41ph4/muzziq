"use client";

import { useState } from "react";
import useSWR from "swr";
import { Plus, X, CheckCircle2, XCircle } from "lucide-react";
import { TopBar } from "@/components/TopBar";
import type { ConfiguredIndexer } from "@/lib/acquisition/indexers/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

export default function IndexersSettingsPage() {
  const { data, mutate } = useSWR<{ indexers: ConfiguredIndexer[] }>("/api/indexers", fetcher);
  const [name, setName] = useState("");
  const [baseUrl, setBaseUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [testResults, setTestResults] = useState<Record<string, { ok: boolean; detail: string } | "pending">>({});

  async function add() {
    if (!name.trim() || !baseUrl.trim()) return;
    await fetch("/api/indexers", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, baseUrl, apiKey, authType: "apikey" }),
    });
    setName("");
    setBaseUrl("");
    setApiKey("");
    mutate();
  }

  async function remove(id: string) {
    await fetch(`/api/indexers/${id}`, { method: "DELETE" });
    mutate();
  }

  async function test(id: string) {
    setTestResults((prev) => ({ ...prev, [id]: "pending" }));
    const res = await fetch(`/api/indexers/${id}/test`, { method: "POST" });
    const result = await res.json();
    setTestResults((prev) => ({ ...prev, [id]: result }));
    mutate();
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <TopBar title="Indexers" />
      <p className="float-in -mt-2 text-sm text-[var(--ink-soft)]">
        Protocole Torznab standard — indique l&apos;URL de l&apos;API de ton indexer et sa clé.
      </p>

      <div className="glass float-in flex flex-col gap-2 rounded-2xl p-4">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Nom (ex: MonIndexer)"
          className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-[var(--brand)]"
        />
        <input
          value={baseUrl}
          onChange={(e) => setBaseUrl(e.target.value)}
          placeholder="https://indexer.example/api"
          className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-[var(--brand)]"
        />
        <input
          type="password"
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
          placeholder="Clé API"
          className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-[var(--brand)]"
        />
        <button
          onClick={add}
          className="brand-gradient mt-1 flex items-center justify-center gap-2 rounded-full px-4 py-2.5 text-[13px] font-bold text-black transition-transform active:scale-95"
        >
          <Plus size={15} />
          Ajouter
        </button>
      </div>

      <ul className="flex flex-col gap-2">
        {data?.indexers?.map((ix) => {
          const result = testResults[ix.id];
          return (
            <li key={ix.id} className="glass flex items-center gap-3 rounded-xl p-3">
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-semibold">{ix.name}</div>
                <div className="truncate text-xs text-[var(--ink-dim)]">{ix.baseUrl}</div>
                {result && result !== "pending" && (
                  <div className={`mt-1 flex items-center gap-1 text-xs ${result.ok ? "text-[var(--brand)]" : "text-red-400"}`}>
                    {result.ok ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                    {result.detail}
                  </div>
                )}
              </div>
              <button
                onClick={() => test(ix.id)}
                disabled={result === "pending"}
                className="rounded-full border border-white/15 px-3 py-1.5 text-xs font-bold text-[var(--ink-soft)] hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-50"
              >
                {result === "pending" ? "…" : "Tester"}
              </button>
              <button
                onClick={() => remove(ix.id)}
                className="flex h-8 w-8 items-center justify-center rounded-full text-[var(--ink-dim)] hover:bg-red-500/10 hover:text-red-400"
              >
                <X size={14} />
              </button>
            </li>
          );
        })}
      </ul>
      {data && data.indexers.length === 0 && (
        <p className="glass rounded-xl px-4 py-6 text-center text-sm text-[var(--ink-dim)]">Aucun indexer configuré.</p>
      )}
    </main>
  );
}
