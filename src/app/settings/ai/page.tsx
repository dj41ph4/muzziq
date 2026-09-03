"use client";

import { useState } from "react";
import useSWR from "swr";
import { CheckCircle2, XCircle, Sparkles } from "lucide-react";
import { TopBar } from "@/components/TopBar";
import type { AiConfig, AiProviderId } from "@/lib/ai/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface ProviderInfo {
  label: string;
  keyUrl: string;
  freeTier: string;
}

function SettingsSkeleton() {
  return (
    <div className="flex flex-col gap-4">
      <div className="glass h-14 animate-pulse rounded-2xl" />
      {[0, 1, 2].map((i) => (
        <div key={i} className="glass flex flex-col gap-2 rounded-2xl p-4">
          <div className="h-3 w-1/3 animate-pulse rounded bg-white/[0.06]" />
          <div className="h-9 animate-pulse rounded-lg bg-white/[0.04]" />
          <div className="h-9 animate-pulse rounded-lg bg-white/[0.04]" />
        </div>
      ))}
    </div>
  );
}

export default function AiSettingsPage() {
  const { data, mutate } = useSWR<{ config: AiConfig; providerInfo: Record<AiProviderId, ProviderInfo> }>(
    "/api/settings/ai",
    fetcher
  );
  const [testResults, setTestResults] = useState<Record<string, { ok: boolean; detail: string } | "pending">>({});

  async function save(patch: Partial<AiConfig>) {
    const res = await fetch("/api/settings/ai", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(patch),
    });
    const body = await res.json();
    mutate(body, { revalidate: false });
  }

  function setProviderKey(providerId: AiProviderId, key: string, config: AiConfig) {
    save({
      providers: { ...config.providers, [providerId]: { ...config.providers[providerId], keys: key ? [{ id: "primary", key }] : [] } },
    });
  }

  function setProviderModel(providerId: AiProviderId, model: string, config: AiConfig) {
    save({ providers: { ...config.providers, [providerId]: { ...config.providers[providerId], model } } });
  }

  async function testKey(providerId: AiProviderId, config: AiConfig) {
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
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 pb-16 sm:px-8">
      <TopBar title="MuzziQ AI" />
      <p className="float-in -mt-2 text-sm text-[var(--ink-soft)]">
        Invisible tant que désactivé. Configure au moins une clé pour l&apos;activer — chaque fournisseur a un
        palier gratuit exploitable.
      </p>

      {!data && <SettingsSkeleton />}

      {data && (
        <>
          <label className="glass float-in flex items-center gap-3 rounded-2xl p-4">
            <input
              type="checkbox"
              checked={data.config.enabled}
              onChange={(e) => save({ enabled: e.target.checked })}
              className="h-4 w-4 accent-[var(--brand)]"
            />
            <div className="flex items-center gap-2">
              <Sparkles size={15} className="text-[var(--brand)]" />
              <span className="text-[14px] font-semibold">Activer MuzziQ AI</span>
            </div>
          </label>

          {(Object.keys(data.providerInfo) as AiProviderId[]).map((providerId) => {
            const info = data.providerInfo[providerId];
            const provider = data.config.providers[providerId];
            const result = testResults[providerId];
            return (
              <div key={providerId} className="glass float-in flex flex-col gap-2.5 rounded-2xl p-4">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold uppercase tracking-wide text-[var(--ink-dim)]">{info.label}</span>
                  <a
                    href={info.keyUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="text-xs font-semibold text-[var(--brand)] underline-offset-2 hover:underline"
                  >
                    Obtenir une clé →
                  </a>
                </div>
                <p className="text-xs text-[var(--ink-dim)]">{info.freeTier}</p>
                <input
                  type="password"
                  placeholder="Clé API"
                  defaultValue={provider.keys[0]?.key ?? ""}
                  onBlur={(e) => setProviderKey(providerId, e.target.value, data.config)}
                  className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-[var(--brand)]"
                />
                <input
                  type="text"
                  placeholder="Modèle"
                  defaultValue={provider.model}
                  onBlur={(e) => setProviderModel(providerId, e.target.value, data.config)}
                  className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-[var(--brand)]"
                />
                <div className="flex items-center gap-2.5">
                  <button
                    onClick={() => testKey(providerId, data.config)}
                    disabled={!provider.keys[0]?.key || result === "pending"}
                    className="rounded-full border border-white/15 px-3 py-1.5 text-xs font-bold text-[var(--ink-soft)] transition-colors active:scale-95 hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-40"
                  >
                    {result === "pending" ? "Test en cours…" : "Tester"}
                  </button>
                  {result && result !== "pending" && (
                    <span className={`flex items-center gap-1 text-xs ${result.ok ? "text-[var(--brand)]" : "text-red-400"}`}>
                      {result.ok ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                      {result.detail}
                    </span>
                  )}
                </div>
              </div>
            );
          })}

          <div className="glass float-in flex items-center justify-between rounded-2xl p-4">
            <span className="text-[14px] font-semibold">Fournisseur principal</span>
            <select
              value={data.config.primary}
              onChange={(e) => save({ primary: e.target.value as AiProviderId })}
              className="rounded-lg border border-white/10 bg-black/30 px-2.5 py-1.5 text-sm outline-none focus:border-[var(--brand)]"
            >
              {(Object.keys(data.providerInfo) as AiProviderId[]).map((id) => (
                <option key={id} value={id}>
                  {data.providerInfo[id].label}
                </option>
              ))}
            </select>
          </div>

          <label className="glass float-in flex items-center gap-3 rounded-2xl p-4">
            <input
              type="checkbox"
              checked={data.config.fallback}
              onChange={(e) => save({ fallback: e.target.checked })}
              className="h-4 w-4 accent-[var(--brand)]"
            />
            <span className="text-[14px] font-semibold">Repli automatique sur le fournisseur suivant en cas d&apos;échec/quota</span>
          </label>
        </>
      )}
    </main>
  );
}
