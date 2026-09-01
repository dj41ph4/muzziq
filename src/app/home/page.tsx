"use client";

import useSWR from "swr";
import { Music2 } from "lucide-react";
import { Logo } from "@/components/Logo";
import { usePlayer } from "@/components/PlayerContext";

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

  async function playRecording(r: Recording) {
    const res = await fetch(`/api/recordings/${r.id}/resolve`);
    if (!res.ok) return; // Aucune source réelle trouvée — jamais fabriquer une lecture qui échouera.
    const resolved: { kind: "local" | "provider"; id: string } = await res.json();
    play({ kind: resolved.kind, id: resolved.id, title: r.title, artist: r.artist, thumbnailUrl: r.thumbnailUrl });
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-4xl flex-col gap-10 px-5 pt-8 sm:px-8">
      <header className="float-in flex items-center gap-4">
        <Logo size="lg" />
        <div>
          <p className="text-sm font-medium text-[var(--ink-dim)]">MUZZIK</p>
          <h1 className="mt-0.5 text-4xl font-extrabold tracking-tight sm:text-5xl">{greeting()}</h1>
        </div>
      </header>

      {!data && <RowSkeleton />}

      {data?.rows.length === 0 && (
        <div className="glass float-in flex flex-col items-center gap-3 rounded-2xl px-8 py-16 text-center">
          <Music2 size={32} className="text-[var(--ink-dim)]" />
          <p className="max-w-xs text-sm text-[var(--ink-soft)]">
            Rien à montrer pour l&apos;instant — cherche et joue quelques morceaux pour que MUZZIK apprenne tes goûts.
          </p>
        </div>
      )}

      {data?.rows.map((row, i) => (
        <section key={row.id} className="float-in" style={{ animationDelay: `${i * 60}ms` }}>
          <h2 className="mb-4 text-xl font-bold tracking-tight">{row.title}</h2>
          <div className="-mx-5 flex gap-4 overflow-x-auto px-5 pb-2 sm:-mx-8 sm:px-8" style={{ scrollbarWidth: "none" }}>
            {row.recordings.map((r) => (
              <div key={r.id} onClick={() => playRecording(r)} className="group w-36 flex-shrink-0 cursor-pointer">
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
