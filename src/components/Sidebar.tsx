"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import useSWR from "swr";
import { Home, Search, Library, ListMusic, Settings } from "lucide-react";
import { Logo } from "./Logo";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

const NAV_ITEMS = [
  { href: "/home", label: "Accueil", Icon: Home },
  { href: "/search", label: "Recherche", Icon: Search },
  { href: "/library", label: "Bibliothèque", Icon: Library },
];

interface Playlist {
  id: string;
  name: string;
  itemCount: number;
}

/**
 * Navigation desktop persistante (colonne gauche façon Spotify) — BottomNav
 * reste le langage mobile (§56.1), jamais mélangé : Sidebar n'apparaît qu'à
 * partir du breakpoint lg (voir layout.tsx), BottomNav se cache à ce même
 * point plutôt que de coexister avec une deuxième navigation.
 */
export function Sidebar() {
  const pathname = usePathname();
  const { data } = useSWR<{ playlists: Playlist[] }>("/api/playlists", fetcher);

  return (
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col gap-2 p-3 lg:flex">
      <Link href="/home" className="glass mb-2 flex items-center gap-3 rounded-2xl px-4 py-4">
        <Logo size="sm" animated={false} />
        <span className="text-lg font-extrabold tracking-tight">MuzziQ</span>
      </Link>

      <nav className="glass flex flex-col gap-1 rounded-2xl p-2">
        {NAV_ITEMS.map(({ href, label, Icon }) => {
          const active = pathname === href;
          return (
            <Link
              key={href}
              href={href}
              className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-[15px] font-semibold transition-colors ${
                active ? "bg-white/[0.08] text-[var(--ink)]" : "text-[var(--ink-soft)] hover:text-[var(--ink)]"
              }`}
            >
              <Icon size={20} strokeWidth={active ? 2.3 : 1.9} />
              {label}
            </Link>
          );
        })}
      </nav>

      <div className="glass flex min-h-0 flex-1 flex-col rounded-2xl p-2">
        <div className="flex items-center justify-between px-2 py-2">
          <span className="text-[13px] font-bold text-[var(--ink-soft)]">Playlists</span>
        </div>
        <div className="flex-1 overflow-y-auto" style={{ scrollbarWidth: "thin" }}>
          {!data && (
            <div className="flex flex-col gap-1 px-1">
              {[0, 1, 2].map((i) => (
                <div key={i} className="h-9 animate-pulse rounded-lg bg-white/[0.04]" />
              ))}
            </div>
          )}
          {data?.playlists.length === 0 && (
            <p className="px-3 py-4 text-[13px] text-[var(--ink-dim)]">Aucune playlist pour l&apos;instant.</p>
          )}
          {data?.playlists.map((p) => (
            <Link
              key={p.id}
              href={`/playlists/${p.id}`}
              className={`flex items-center gap-3 rounded-xl px-3 py-2 text-sm transition-colors ${
                pathname === `/playlists/${p.id}` ? "bg-white/[0.08] text-[var(--ink)]" : "text-[var(--ink-soft)] hover:text-[var(--ink)]"
              }`}
            >
              <ListMusic size={16} className="flex-shrink-0 text-[var(--ink-dim)]" />
              <span className="truncate">{p.name}</span>
            </Link>
          ))}
        </div>
      </div>

      <Link
        href="/settings"
        className={`glass flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-semibold transition-colors ${
          pathname.startsWith("/settings") ? "text-[var(--ink)]" : "text-[var(--ink-soft)] hover:text-[var(--ink)]"
        }`}
      >
        <Settings size={18} />
        Réglages
      </Link>
    </aside>
  );
}
