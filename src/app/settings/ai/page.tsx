"use client";

import { useState } from "react";
import useSWR from "swr";
import type { AiConfig, AiProviderId } from "@/lib/ai/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface ProviderInfo {
  label: string;
  keyUrl: string;
  freeTier: string;
}

export default function AiSettingsPage() {
  const { data, mutate } = useSWR<{ config: AiConfig; providerInfo: Record<AiProviderId, ProviderInfo> }>(
    "/api/settings/ai",
    fetcher
  );
  const [testResults, setTestResults] = useState<Record<string, { ok: boolean; detail: string } | "pending">>({});

  if (!data) return null;
  const { config, providerInfo } = data;

  async function save(patch: Partial<AiConfig>) {
    const res = await fetch("/api/settings/ai", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(patch),
    });
    const body = await res.json();
    mutate(body, { revalidate: false });
  }

  function setProviderKey(providerId: AiProviderId, key: string) {
    save({
      providers: { ...config.providers, [providerId]: { ...config.providers[providerId], keys: key ? [{ id: "primary", key }] : [] } },
    });
  }

  function setProviderModel(providerId: AiProviderId, model: string) {
    save({ providers: { ...config.providers, [providerId]: { ...config.providers[providerId], model } } });
  }

  async function testKey(providerId: AiProviderId) {
    const provider = config.providers[providerId];
    const key = provider.keys[0]?.key ?? "";
    if (!key) return;
    setTestResults((prev) => ({ ...prev, [providerId]: "pending" }));
    const res = await fetch("/api/settings/ai/test", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ providerId, model: provider.model, key }),
    });
    const result = await res.json();
    setTestResults((prev) => ({ ...prev, [providerId]: result }));
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-6 py-10">
      <h1 className="text-2xl font-bold">MUZZIK AI</h1>
      <p className="text-sm text-[var(--ink-soft)]">
        Invisible tant que désactivé. Configure au moins une clé pour l&apos;activer — chaque fournisseur a un
        palier gratuit exploitable.
      </p>

      <label className="flex items-center gap-2 rounded-xl border border-white/10 bg-[var(--panel)] p-3">
        <input type="checkbox" checked={config.enabled} onChange={(e) => save({ enabled: e.target.checked })} />
        <span className="font-semibold">Activer MUZZIK AI</span>
      </label>

      {(Object.keys(providerInfo) as AiProviderId[]).map((providerId) => {
        const info = providerInfo[providerId];
        const provider = config.providers[providerId];
        const result = testResults[providerId];
        return (
          <div key={providerId} className="flex flex-col gap-2 rounded-xl border border-white/10 bg-[var(--panel)] p-4">
            <div className="flex items-center justify-between">
              <span className="font-semibold">{info.label}</span>
              <a
                href={info.keyUrl}
                target="_blank"
                rel="noreferrer"
                className="text-xs text-[var(--brand)] underline underline-offset-2"
              >
                Obtenir une clé →
              </a>
            </div>
            <p className="text-xs text-[var(--ink-dim)]">{info.freeTier}</p>
            <input
              type="password"
              placeholder="Clé API"
              defaultValue={provider.keys[0]?.key ?? ""}
              onBlur={(e) => setProviderKey(providerId, e.target.value)}
              className="rounded-lg border border-white/10 bg-black/30 px-3 py-1.5 text-sm outline-none focus:border-[var(--brand)]"
            />
            <input
              type="text"
              placeholder="Modèle"
              defaultValue={provider.model}
              onBlur={(e) => setProviderModel(providerId, e.target.value)}
              className="rounded-lg border border-white/10 bg-black/30 px-3 py-1.5 text-sm outline-none focus:border-[var(--brand)]"
            />
            <div className="flex items-center gap-2">
              <button
                onClick={() => testKey(providerId)}
                disabled={!provider.keys[0]?.key}
                className="rounded-lg border border-white/15 px-3 py-1 text-xs font-bold hover:border-[var(--brand)]/50 disabled:opacity-40"
              >
                Tester
              </button>
              {result === "pending" && <span className="text-xs text-[var(--ink-dim)]">Test en cours…</span>}
              {result && result !== "pending" && (
                <span className={`text-xs ${result.ok ? "text-[var(--brand)]" : "text-red-400"}`}>
                  {result.ok ? "✓ " : "✗ "}
                  {result.detail}
                </span>
              )}
            </div>
          </div>
        );
      })}

      <label className="flex items-center justify-between rounded-xl border border-white/10 bg-[var(--panel)] p-3">
        <span>Fournisseur principal</span>
        <select
          value={config.primary}
          onChange={(e) => save({ primary: e.target.value as AiProviderId })}
          className="rounded-lg border border-white/10 bg-black/30 px-2 py-1 text-sm"
        >
          {(Object.keys(providerInfo) as AiProviderId[]).map((id) => (
            <option key={id} value={id}>
              {providerInfo[id].label}
            </option>
          ))}
        </select>
      </label>

      <label className="flex items-center gap-2 rounded-xl border border-white/10 bg-[var(--panel)] p-3">
        <input type="checkbox" checked={config.fallback} onChange={(e) => save({ fallback: e.target.checked })} />
        <span>Repli automatique sur le fournisseur suivant en cas d&apos;échec/quota</span>
      </label>
    </main>
  );
}
