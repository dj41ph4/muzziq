import Link from "next/link";
import { Sparkles, Rss, Server, ChevronRight } from "lucide-react";
import { TopBar } from "@/components/TopBar";

const ENTRIES = [
  { href: "/settings/ai", label: "MuzziQ AI", desc: "Fournisseurs, clés API", Icon: Sparkles },
  { href: "/settings/indexers", label: "Indexers", desc: "Sources d'acquisition torrent", Icon: Rss },
  { href: "/settings/plex", label: "Plex", desc: "Intégration optionnelle", Icon: Server },
];

export default function SettingsHubPage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <TopBar title="Réglages" />
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
