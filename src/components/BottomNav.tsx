"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Home, Search, Library } from "lucide-react";

const ITEMS = [
  { href: "/home", label: "Accueil", Icon: Home },
  { href: "/search", label: "Recherche", Icon: Search },
  { href: "/library", label: "Bibliothèque", Icon: Library },
];

/** Navigation basse persistante (langage Spotify) — jamais un nouveau menu par page. */
export function BottomNav() {
  const pathname = usePathname();

  return (
    <nav className="glass fixed inset-x-0 bottom-0 z-40 border-t-0">
      <div className="mx-auto flex max-w-3xl items-center justify-around px-4 pb-[calc(env(safe-area-inset-bottom)+6px)] pt-2.5">
        {ITEMS.map(({ href, label, Icon }) => {
          const active = pathname === href;
          return (
            <Link
              key={href}
              href={href}
              className="group flex flex-col items-center gap-1 px-4 py-1 transition-transform active:scale-95"
            >
              <Icon
                size={22}
                strokeWidth={active ? 2.4 : 1.8}
                className={active ? "text-[var(--ink)]" : "text-[var(--ink-dim)] group-hover:text-[var(--ink-soft)]"}
              />
              <span className={`text-[10px] font-semibold tracking-tight ${active ? "text-[var(--ink)]" : "text-[var(--ink-dim)]"}`}>
                {label}
              </span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
