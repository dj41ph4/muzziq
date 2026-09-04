"use client";

import Link from "next/link";
import useSWR from "swr";
import { useState } from "react";
import { Sparkles, Rss, Server, ChevronRight, FolderCog, Eye, EyeOff } from "lucide-react";
import { TopBar } from "@/components/TopBar";

const ENTRIES = [
  { href: "/settings/library", label: "Bibliothèque", desc: "Dossier musique, scan", Icon: FolderCog },
  { href: "/settings/ai", label: "MuzziQ AI", desc: "Fournisseurs, clés API", Icon: Sparkles },
  { href: "/settings/indexers", label: "Indexers", desc: "Sources d'acquisition torrent", Icon: Rss },
  { href: "/settings/plex", label: "Plex", desc: "Intégration optionnelle", Icon: Server },
];

const fetcher = (url: string) => fetch(url).then((response) => response.json());

function LocalVisibilitySetting() {
  const { data, mutate } = useSWR<{ showLocalFiles: boolean }>("/api/settings", fetcher);
  const [busy, setBusy] = useState(false);
  const visible = data?.showLocalFiles ?? false;

  async function toggle() {
    setBusy(true);
    await fetch("/api/settings", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ showLocalFiles: !visible }),
    });
    await mutate();
    setBusy(false);
  }

  return (
    <button onClick={toggle} disabled={busy || !data} className="glass float-in flex w-full items-center gap-3 rounded-xl p-3.5 text-left transition-colors hover:bg-white/[0.06] disabled:opacity-60">
      <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-white/[0.06]">
        {visible ? <Eye size={17} className="text-[var(--brand)]" /> : <EyeOff size={17} className="text-[var(--ink-soft)]" />}
      </div>
      <div className="min-w-0 flex-1">
        <div className="text-[14px] font-semibold">Fichiers locaux sur l&apos;accueil</div>
        <div className="text-[12px] text-[var(--ink-dim)]">{visible ? "Visibles dans les recommandations" : "Masqués des recommandations — disponibles dans Bibliothèque"}</div>
      </div>
      <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold uppercase ${visible ? "bg-[var(--brand)]/15 text-[var(--brand)]" : "bg-white/[0.06] text-[var(--ink-dim)]"}`}>{visible ? "Oui" : "Non"}</span>
    </button>
  );
}

export default function SettingsHubPage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <TopBar title="Réglages" />
      <LocalVisibilitySetting />
      <ul className="flex flex-col gap-1">
        {ENTRIES.map(({ href, label, desc, Icon }) => (
          <Link key={href} href={href} className="glass float-in flex items-center gap-3 rounded-xl p-3.5">
            <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-white/[0.06]">
              <Icon size={17} className="text-[var(--ink-soft)]" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-[14px] font-semibold">{label}</div>
              <div className="text-[12px] text-[var(--ink-dim)]">{desc}</div>
            </div>
            <ChevronRight size={16} className="text-[var(--ink-dim)]" />
          </Link>
        ))}
      </ul>
    </main>
  );
}
