"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const ITEMS = [
  { href: "/home", label: "Accueil", icon: "⌂" },
  { href: "/search", label: "Recherche", icon: "⌕" },
  { href: "/library", label: "Bibliothèque", icon: "☰" },
];

/** Navigation basse persistante (langage Spotify) — jamais un nouveau menu par page. */
export function BottomNav() {
  const pathname = usePathname();

  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-white/10 bg-[var(--panel)]">
      <div className="mx-auto flex max-w-3xl items-center justify-around px-4 py-2">
        {ITEMS.map((item) => {
          const active = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex flex-col items-center gap-0.5 px-3 py-1 text-xs ${
                active ? "text-[var(--brand)]" : "text-[var(--ink-dim)]"
              }`}
            >
              <span className="text-lg leading-none">{item.icon}</span>
              {item.label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
