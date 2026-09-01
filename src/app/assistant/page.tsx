"use client";

import { useState } from "react";
import { Sparkles, Send } from "lucide-react";
import { TopBar } from "@/components/TopBar";

interface Message {
  role: "user" | "assistant";
  content: string;
  actionDetail?: string;
}

export default function AssistantPage() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function send() {
    const text = input.trim();
    if (!text || sending) return;
    setInput("");
    setError(null);
    setMessages((m) => [...m, { role: "user", content: text }]);
    setSending(true);

    try {
      const res = await fetch("/api/ai/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: text }),
      });
      const body = await res.json();
      if (!res.ok) {
        setError(body.error);
      } else {
        setMessages((m) => [...m, { role: "assistant", content: body.reply, actionDetail: body.actionResult?.detail }]);
      }
    } finally {
      setSending(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 sm:px-8">
      <TopBar title="Assistant" />

      {error && <p className="glass float-in rounded-xl px-4 py-2.5 text-sm text-red-400">{error}</p>}

      <div className="flex flex-1 flex-col gap-3">
        {messages.length === 0 && !error && (
          <div className="glass float-in flex flex-col items-center gap-3 rounded-2xl px-8 py-14 text-center">
            <Sparkles size={28} className="text-[var(--brand)]" />
            <p className="max-w-xs text-sm text-[var(--ink-soft)]">
              Demande-moi de chercher, ajouter un morceau à ta bibliothèque, ou juste discute musique.
            </p>
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={`float-in flex ${m.role === "user" ? "justify-end" : "justify-start"}`}>
            <div
              className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-[14px] ${
                m.role === "user" ? "brand-gradient text-black" : "glass"
              }`}
            >
              {m.content}
              {m.actionDetail && <div className="mt-1 text-xs opacity-70">{m.actionDetail}</div>}
            </div>
          </div>
        ))}
        {sending && <p className="text-xs text-[var(--ink-dim)]">MUZZIK AI réfléchit…</p>}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          send();
        }}
        className="sticky bottom-32 flex gap-2"
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Écris un message…"
          className="glass flex-1 rounded-full px-4 py-3 text-[14px] outline-none placeholder:text-[var(--ink-dim)] focus:border-[var(--brand)]/50"
        />
        <button
          type="submit"
          disabled={sending || !input.trim()}
          className="brand-gradient flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full text-black disabled:opacity-50"
        >
          <Send size={16} />
        </button>
      </form>
    </main>
  );
}
