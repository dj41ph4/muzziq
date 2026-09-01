"use client";

import useSWR from "swr";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

export default function Home() {
  const { data: health } = useSWR("/api/health", fetcher, { refreshInterval: 5000 });
  const { data: settings } = useSWR("/api/settings", fetcher);

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col items-center justify-center gap-6 px-6 text-center">
      <h1 className="text-4xl font-bold tracking-tight">MUZZIK</h1>
      <p className="text-[var(--ink-soft)]">
        Fondation Phase A — boot, settings, health. Aucun provider branché pour l&apos;instant.
      </p>

      <div className="w-full rounded-2xl border border-white/10 bg-[var(--panel)] p-6 text-left text-sm">
        <div className="mb-3 flex items-center gap-2">
          <span
            className={`h-2 w-2 rounded-full ${health?.status === "ok" ? "bg-[var(--brand)]" : "bg-zinc-600"}`}
          />
          <span className="font-semibold">{settings?.serverName ?? "MUZZIK"}</span>
          <span className="text-[var(--ink-dim)]">v{health?.version ?? "…"}</span>
        </div>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-[var(--ink-soft)]">
          <dt>Statut API</dt>
          <dd>{health?.status ?? "…"}</dd>
          <dt>Dossier de données</dt>
          <dd className="truncate">{health?.dataDir ?? "…"}</dd>
          <dt>Dossier musique</dt>
          <dd>{settings?.musicDir ?? "non configuré"}</dd>
        </dl>
      </div>
    </main>
  );
}
