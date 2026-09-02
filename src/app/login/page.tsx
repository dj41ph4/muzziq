"use client";

import { useState } from "react";
import useSWR from "swr";
import { useRouter } from "next/navigation";
import { Logo } from "@/components/Logo";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

export default function LoginPage() {
  const { data } = useSWR<{ user: unknown; setupRequired: boolean }>("/api/auth/me", fetcher);
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!data) return null;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const endpoint = data!.setupRequired ? "/api/auth/setup" : "/api/auth/login";
    const res = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    const body = await res.json();
    setBusy(false);
    if (!res.ok) {
      setError(body.error);
    } else {
      router.push("/home");
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-sm flex-col items-center justify-center gap-6 px-6">
      <Logo size="lg" />
      <div className="text-center">
        <h1 className="text-2xl font-extrabold tracking-tight">{data.setupRequired ? "Configurer MuzziQ" : "Connexion"}</h1>
        <p className="mt-1 text-sm text-[var(--ink-soft)]">
          {data.setupRequired ? "Crée le compte administrateur." : "Content de te revoir."}
        </p>
      </div>

      <form onSubmit={submit} className="flex w-full flex-col gap-2.5">
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="Nom d'utilisateur"
          autoFocus
          className="glass rounded-full px-4 py-3 text-[14px] outline-none placeholder:text-[var(--ink-dim)] focus:border-[var(--brand)]/50"
        />
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Mot de passe"
          className="glass rounded-full px-4 py-3 text-[14px] outline-none placeholder:text-[var(--ink-dim)] focus:border-[var(--brand)]/50"
        />
        {error && <p className="px-2 text-xs text-red-400">{error}</p>}
        <button
          type="submit"
          disabled={busy || !username || !password}
          className="brand-gradient mt-1 rounded-full px-4 py-3 text-[14px] font-bold text-black transition-transform active:scale-95 disabled:opacity-50"
        >
          {busy ? "…" : data.setupRequired ? "Créer le compte" : "Se connecter"}
        </button>
      </form>
    </main>
  );
}
