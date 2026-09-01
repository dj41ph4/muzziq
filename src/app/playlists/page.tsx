"use client";

import { useState } from "react";
import useSWR from "swr";
import Link from "next/link";
import { Plus, ListMusic, X } from "lucide-react";
import { TopBar } from "@/components/TopBar";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface PlaylistView {
  id: string;
  name: string;
  itemCount: number;
}

export default function PlaylistsPage() {
  const { data, mutate } = useSWR<{ playlists: PlaylistView[] }>("/api/playlists", fetcher);
  const [name, setName] = useState("");

  async function create() {
    if (!name.trim()) return;
    await fetch("/api/playlists", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: name.trim() }),
    });
    setName("");
    mutate();
  }

  async function remove(id: string, e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    await fetch(`/api/playlists/${id}`, { method: "DELETE" });
    mutate();
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <TopBar title="Playlists" />

      <form
        onSubmit={(e) => {
          e.preventDefault();
          create();
        }}
        className="float-in relative"
      >
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Nouvelle playlist…"
          className="glass w-full rounded-full py-3 pl-4 pr-14 text-[14px] outline-none placeholder:text-[var(--ink-dim)] focus:border-[var(--brand)]/50"
        />
        <button
          type="submit"
          disabled={!name.trim()}
          className="brand-gradient absolute right-1.5 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full text-black disabled:opacity-0"
        >
          <Plus size={16} />
        </button>
      </form>

      <ul className="flex flex-col gap-1">
        {data?.playlists?.map((p) => (
          <Link
            key={p.id}
            href={`/playlists/${p.id}`}
            className="group flex items-center gap-3 rounded-xl p-2.5 transition-colors hover:bg-white/[0.04]"
          >
            <div className="art-fallback flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-lg">
              <ListMusic size={16} className="text-white/25" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-[14px] font-semibold">{p.name}</div>
              <div className="text-[12px] text-[var(--ink-soft)]">{p.itemCount} morceaux</div>
            </div>
            <button
              onClick={(e) => remove(p.id, e)}
              className="flex h-8 w-8 items-center justify-center rounded-full text-[var(--ink-dim)] opacity-0 transition-opacity hover:bg-red-500/10 hover:text-red-400 group-hover:opacity-100"
            >
              <X size={14} />
            </button>
          </Link>
        ))}
      </ul>
      {data && data.playlists.length === 0 && (
        <p className="glass rounded-xl px-4 py-6 text-center text-sm text-[var(--ink-dim)]">Aucune playlist pour l&apos;instant.</p>
      )}
    </main>
  );
}
