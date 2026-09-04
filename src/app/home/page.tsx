"use client";

import useSWR from "swr";
import { useState } from "react";
import Link from "next/link";
import { Music2, Heart, Search, Download, Sparkles } from "lucide-react";
import { Logo } from "@/components/Logo";
import { usePlayer, type PlayableTrack } from "@/components/PlayerContext";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface Recording {
  id: string;
  title: string;
  artist: string;
  thumbnailUrl?: string;
}

interface HomeRow {
  id: string;
  title: string;
  recordings: Recording[];
}

const QUICK_LINKS = [
  { href: "/favorites", label: "Titres likés", Icon: Heart },
  { href: "/search", label: "Recherche", Icon: Search },
  { href: "/offline", label: "Hors ligne", Icon: Download },
  { href: "/assistant", label: "Assistant", Icon: Sparkles },
];

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 5) return "Bonne nuit";
  if (hour < 12) return "Bonjour";
  if (hour < 18) return "Bon après-midi";
  return "Bonsoir";
}

function Cover({ url }: { url?: string }) {
  return (
    <div className="art-fallback aspect-square w-full overflow-hidden rounded-xl shadow-[var(--shadow-card)]">
      {url ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt="" className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105" />
      ) : (
        <div className="flex h-full w-full items-center justify-center">
          <Music2 size={28} className="text-white/20" />
        </div>
      )}
    </div>
  );
}

function RowSkeleton() {
  return (
    <div className="flex gap-4">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="w-36 flex-shrink-0 animate-pulse">
          <div className="aspect-square w-full rounded-xl bg-white/[0.06]" />
          <div className="mt-2 h-3 w-4/5 rounded bg-white/[0.06]" />
          <div className="mt-1.5 h-2.5 w-3/5 rounded bg-white/[0.04]" />
        </div>
      ))}
    </div>
  );
}

export default function HomePage() {
  const { data } = useSWR<{ rows: HomeRow[] }>("/api/home", fetcher);
  const { play } = usePlayer();
  const [filter, setFilter] = useState<"all" | "for-you" | "trending" | "new-releases">("all");
  const visibleRows = data?.rows.filter((row) => filter === "all" || row.id === filter);
  const featured = visibleRows?.find((row) => row.recordings.length > 0)?.recordings[0];
  const featuredRow = visibleRows?.find((row) => row.recordings.some((recording) => recording.id === featured?.id));

  async function resolveOne(r: Recording): Promise<PlayableTrack | null> {
    const res = await fetch(`/api/recordings/${r.id}/resolve`);
    if (!res.ok) return null; // Aucune source réelle trouvée — jamais fabriquer une lecture qui échouera.
    const resolved: { kind: "local" | "offline" | "provider"; id: string } = await res.json();
    return { kind: resolved.kind, id: resolved.id, recordingId: r.id, title: r.title, artist: r.artist, thumbnailUrl: r.thumbnailUrl };
  }

  async function playRecording(r: Recording, row: Recording[]) {
    // Résout la ligne entière (pas seulement le morceau cliqué) pour que
    // suivant/précédent enchaînent sur de vraies sources jouables plutôt
    // que sur des recordingId non résolus.
    const resolvedRow = await Promise.all(row.map(resolveOne));
    const queue = resolvedRow.filter((t): t is PlayableTrack => t !== null);
    const clickedIdx = row.findIndex((x) => x.id === r.id);
    const target = resolvedRow[clickedIdx];
    if (!target || queue.length === 0) return;
    play(target, queue);
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-4xl flex-col gap-10 px-5 pt-8 sm:px-8">
      <header className="float-in flex items-center gap-4">
        <Logo size="lg" />
        <div>
          <p className="text-sm font-medium text-[var(--ink-dim)]">MuzziQ</p>
          <h1 className="mt-0.5 text-4xl font-extrabold tracking-tight sm:text-5xl">{greeting()}</h1>
        </div>
      </header>

      <div className="float-in grid grid-cols-2 gap-3 sm:grid-cols-4">
        {QUICK_LINKS.map(({ href, label, Icon }) => (
            <Link
              key={href}
              href={href}
              className="glass flex items-center gap-3 overflow-hidden rounded-lg pr-4 transition-colors hover:bg-white/[0.06]"
            >
              <div className="brand-gradient flex h-14 w-14 flex-shrink-0 items-center justify-center">
                <Icon size={18} className="text-black" />
              </div>
              <span className="truncate text-[14px] font-bold tracking-tight">{label}</span>
            </Link>
          ))}
      </div>

      <nav className="float-in flex gap-2 overflow-x-auto pb-1" aria-label="Filtres de l'accueil" style={{ scrollbarWidth: "none" }}>
        {([
          ["all", "Tout"],
          ["for-you", "Pour toi"],
          ["trending", "Hits du moment"],
          ["new-releases", "Nouveautés"],
        ] as const).map(([value, label]) => (
          <button key={value} type="button" onClick={() => setFilter(value)} className={`flex-shrink-0 rounded-full px-4 py-2 text-xs font-bold transition-colors ${filter === value ? "bg-[var(--brand)] text-black" : "glass text-[var(--ink-soft)] hover:text-[var(--ink)]"}`}>
            {label}
          </button>
        ))}
      </nav>

      {featured && featuredRow && (
        <button
          type="button"
          onClick={() => playRecording(featured, featuredRow.recordings)}
          className="group float-in relative overflow-hidden rounded-3xl border border-white/10 bg-[radial-gradient(circle_at_85%_10%,rgba(29,185,84,.38),transparent_38%),linear-gradient(120deg,#171a1f,#111316)] p-6 text-left shadow-[var(--shadow-card)] transition-transform duration-300 hover:-translate-y-1 sm:p-8"
        >
          {featured.thumbnailUrl && <img src={featured.thumbnailUrl} alt="" className="absolute inset-0 h-full w-full object-cover opacity-20 blur-2xl transition-opacity group-hover:opacity-30" />}
          <div className="relative max-w-xl">
            <p className="text-xs font-black uppercase tracking-[0.2em] text-[var(--brand)]">{featuredRow.title}</p>
            <h2 className="mt-3 text-3xl font-black tracking-tight sm:text-4xl">Une écoute choisie pour toi</h2>
            <p className="mt-2 truncate text-sm text-[var(--ink-soft)]">{featured.title} · {featured.artist}</p>
            <span className="mt-6 inline-flex items-center rounded-full bg-[var(--brand)] px-5 py-2.5 text-sm font-extrabold text-black transition-transform group-hover:scale-105">Écouter maintenant</span>
          </div>
        </button>
      )}

      {!data && <RowSkeleton />}

      {data && data.rows.length === 0 && (
        <div className="glass float-in flex flex-col items-center gap-3 rounded-2xl px-8 py-16 text-center">
          <Music2 size={32} className="text-[var(--ink-dim)]" />
          <p className="max-w-xs text-sm text-[var(--ink-soft)]">
            Fais quelques écoutes ou ajoute des titres likés : MuzziQ construira tes recommandations à partir de tes vrais signaux.
          </p>
        </div>
      )}

      {data && data.rows.length > 0 && visibleRows?.length === 0 && (
        <div className="glass float-in flex flex-col items-center gap-3 rounded-2xl px-8 py-12 text-center">
          <Music2 size={28} className="text-[var(--ink-dim)]" />
          <p className="text-sm text-[var(--ink-soft)]">Cette sélection n'est pas encore disponible.</p>
          <button type="button" onClick={() => setFilter("all")} className="text-xs font-bold text-[var(--brand)]">Afficher tout</button>
        </div>
      )}

      {visibleRows?.map((row, i) => (
        <section key={row.id} className="float-in" style={{ animationDelay: `${i * 60}ms` }}>
          <div className="mb-4 flex items-end justify-between gap-4">
            <div>
              <h2 className="text-xl font-bold tracking-tight">{row.title}</h2>
              <p className="mt-1 text-xs text-[var(--ink-dim)]">{row.id === "for-you" ? "Classé depuis tes écoutes, favoris et skips" : row.id === "trending" ? "Sélection actualisée depuis le catalogue" : "Une sélection à découvrir"}</p>
            </div>
            <span className="hidden text-xs font-bold text-[var(--ink-dim)] sm:block">{row.recordings.length} titres</span>
          </div>
          <div className="-mx-5 flex gap-4 overflow-x-auto px-5 pb-2 sm:-mx-8 sm:px-8" style={{ scrollbarWidth: "none" }}>
            {row.recordings.map((r) => (
              <div key={r.id} onClick={() => playRecording(r, row.recordings)} className="group w-36 flex-shrink-0 cursor-pointer">
                <Cover url={r.thumbnailUrl} />
                <div className="mt-2 truncate text-[13px] font-semibold tracking-tight">{r.title}</div>
                <div className="truncate text-[12px] text-[var(--ink-soft)]">{r.artist}</div>
              </div>
            ))}
          </div>
        </section>
      ))}
    </main>
  );
}
