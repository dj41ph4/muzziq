"use client";

import useSWR from "swr";

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

export default function HomePage() {
  const { data } = useSWR<{ rows: HomeRow[] }>("/api/home", fetcher);

  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col gap-8 px-6 py-10">
      <h1 className="text-2xl font-bold">{greeting()}</h1>

      {data?.rows.length === 0 && (
        <p className="text-[var(--ink-dim)]">
          Rien à montrer pour l&apos;instant — cherche et joue quelques morceaux pour que MUZZIK apprenne.
        </p>
      )}

      {data?.rows.map((row) => (
        <section key={row.id}>
          <h2 className="mb-3 text-sm font-bold uppercase text-[var(--ink-dim)]">{row.title}</h2>
          <div className="flex gap-3 overflow-x-auto pb-2">
            {row.recordings.map((r) => (
              <div key={r.id} className="w-32 flex-shrink-0">
                <div className="aspect-square w-32 overflow-hidden rounded-xl bg-[var(--panel)]">
                  {r.thumbnailUrl && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={r.thumbnailUrl} alt="" className="h-full w-full object-cover" />
                  )}
                </div>
                <div className="mt-1 truncate text-sm font-semibold">{r.title}</div>
                <div className="truncate text-xs text-[var(--ink-soft)]">{r.artist}</div>
              </div>
            ))}
          </div>
        </section>
      ))}
    </main>
  );
}
