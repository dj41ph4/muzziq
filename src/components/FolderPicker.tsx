"use client";

import { useCallback, useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Folder, FolderOpen, HardDrive, ArrowUp, Check, X, Loader2 } from "lucide-react";

/**
 * Champ dossier avec explorateur côté serveur (porté depuis Movviz
 * FolderPicker.tsx — logique identique, i18n retiré car MuzziQ n'en a pas).
 * MuzziQ est auto-hébergé : parcourir le système de fichiers du SERVEUR,
 * jamais celui du navigateur — permet de voir ce qui est réellement monté
 * dans le conteneur (ex. /music) au lieu de deviner un chemin à l'aveugle.
 */

interface Listing {
  path: string;
  parent: string | null;
  isRoot: boolean;
  drives: string[];
  dirs: { name: string; path: string }[];
}

export function FolderPicker({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <div className="flex gap-2">
        <input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="/music"
          className="h-10 w-full rounded-lg border border-white/10 bg-black/30 px-3 font-mono text-xs text-[var(--ink)] outline-none transition-colors placeholder:text-[var(--ink-dim)] focus:border-[var(--brand)]/40"
        />
        <button
          onClick={() => setOpen(true)}
          className="glass flex h-10 flex-shrink-0 items-center gap-1.5 rounded-lg px-3 text-sm font-semibold text-[var(--ink-soft)] transition-colors hover:text-[var(--ink)]"
        >
          <FolderOpen size={15} /> Parcourir
        </button>
      </div>
      <AnimatePresence>
        {open && (
          <BrowseModal
            initial={value}
            onCancel={() => setOpen(false)}
            onChoose={(p) => {
              onChange(p);
              setOpen(false);
            }}
          />
        )}
      </AnimatePresence>
    </>
  );
}

function BrowseModal({ initial, onCancel, onChoose }: { initial: string; onCancel: () => void; onChoose: (path: string) => void }) {
  const [listing, setListing] = useState<Listing | null>(null);
  const [loading, setLoading] = useState(true);

  const browse = useCallback(async (p: string) => {
    setLoading(true);
    try {
      const res = await fetch(`/api/system/browse?path=${encodeURIComponent(p)}`, { cache: "no-store" });
      setListing(await res.json());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    browse(initial);
  }, [browse, initial]);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      onClick={onCancel}
    >
      <motion.div
        initial={{ opacity: 0, y: -12, scale: 0.98 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, scale: 0.98 }}
        onClick={(e) => e.stopPropagation()}
        className="glass flex max-h-[70vh] w-full max-w-lg flex-col overflow-hidden rounded-2xl shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-white/10 px-5 py-4">
          <h3 className="font-bold">Choisir un dossier</h3>
          <button onClick={onCancel} className="text-[var(--ink-dim)] hover:text-[var(--ink)]">
            <X size={16} />
          </button>
        </div>

        <div className="flex items-center gap-2 border-b border-white/10 px-5 py-3">
          <button
            onClick={() => listing?.parent !== null && browse(listing?.parent ?? "")}
            disabled={loading || (listing ? listing.parent === null : false)}
            className="flex h-8 w-8 items-center justify-center rounded-lg bg-white/5 text-[var(--ink-soft)] transition-colors hover:text-[var(--ink)] disabled:opacity-30"
            title="Dossier parent"
          >
            <ArrowUp size={15} />
          </button>
          <code className="min-w-0 flex-1 break-all rounded-lg bg-black/30 px-3 py-1.5 text-xs text-[var(--ink-soft)]">
            {listing?.path || "Lecteurs"}
          </code>
        </div>

        {listing && listing.drives.length > 0 && (
          <div className="flex flex-wrap gap-1.5 border-b border-white/10 px-5 py-3">
            {listing.drives.map((d) => (
              <button
                key={d}
                onClick={() => browse(d)}
                className="flex items-center gap-1.5 rounded-lg bg-white/5 px-2.5 py-1.5 text-xs font-semibold text-[var(--ink-soft)] transition-colors hover:text-[var(--brand)]"
              >
                <HardDrive size={13} /> {d}
              </button>
            ))}
          </div>
        )}

        <div className="min-h-[180px] flex-1 overflow-y-auto p-2">
          {loading ? (
            <div className="flex h-40 items-center justify-center text-[var(--ink-dim)]">
              <Loader2 size={20} className="animate-spin" />
            </div>
          ) : listing && listing.dirs.length > 0 ? (
            listing.dirs.map((d) => (
              <button
                key={d.path}
                onClick={() => browse(d.path)}
                className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-[var(--ink-soft)] transition-colors hover:bg-white/5 hover:text-[var(--ink)]"
              >
                <Folder size={16} className="flex-shrink-0 text-[var(--brand)]" />
                <span className="break-all">{d.name}</span>
              </button>
            ))
          ) : (
            <div className="flex h-40 items-center justify-center text-sm text-[var(--ink-dim)]">Dossier vide</div>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-white/10 px-5 py-4">
          <button onClick={onCancel} className="h-10 whitespace-nowrap rounded-xl bg-white/5 px-4 text-sm font-semibold text-[var(--ink-soft)]">
            Annuler
          </button>
          <button
            onClick={() => listing?.path && onChoose(listing.path)}
            disabled={!listing?.path}
            className="brand-gradient flex h-10 items-center gap-2 whitespace-nowrap rounded-xl px-4 text-sm font-bold text-black disabled:opacity-40"
          >
            <Check size={15} /> Choisir ce dossier
          </button>
        </div>
      </motion.div>
    </motion.div>
  );
}
