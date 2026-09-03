"use client";

import { Music2, ListMusic } from "lucide-react";
import { usePlayer } from "./PlayerContext";

/**
 * Panneau contextuel droit (langage Spotify "En écoute") — visible à partir
 * de `xl` uniquement, à côté du contenu principal. Toujours monté (réserve
 * l'espace en layout) pour éviter tout saut de mise en page au démarrage
 * d'une lecture ; affiche un état vide honnête tant qu'aucun morceau ne joue.
 * Données réelles uniquement : morceau courant + file d'attente du
 * PlayerContext, jamais de contenu inventé (recommandations, bio…) tant
 * qu'aucune source réelle n'existe pour ça.
 */
export function ContextPanel() {
  const { track, queue, order, pos, jumpTo } = usePlayer();

  const upcoming = order.slice(pos + 1).map((qIdx, i) => ({ track: queue[qIdx], pos: pos + 1 + i }));

  return (
    <aside className="fixed inset-y-0 right-0 z-20 hidden w-80 flex-col gap-4 overflow-y-auto p-3 pb-28 xl:flex" style={{ scrollbarWidth: "thin" }}>
      <div className="glass flex flex-col gap-3 rounded-2xl p-4">
        <h2 className="text-[13px] font-bold uppercase tracking-wide text-[var(--ink-soft)]">En écoute</h2>
        {track ? (
          <>
            <div className="art-fallback aspect-square w-full overflow-hidden rounded-xl shadow-[var(--shadow-card)]">
              {track.thumbnailUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={track.thumbnailUrl} alt="" className="h-full w-full object-cover" />
              ) : (
                <div className="flex h-full w-full items-center justify-center">
                  <Music2 size={32} className="text-white/20" />
                </div>
              )}
            </div>
            <div>
              <div className="truncate text-[16px] font-bold tracking-tight">{track.title}</div>
              <div className="truncate text-[13px] text-[var(--ink-soft)]">{track.artist}</div>
              {track.album && <div className="truncate text-[12px] text-[var(--ink-dim)]">{track.album}</div>}
            </div>
          </>
        ) : (
          <div className="flex flex-col items-center gap-2 py-8 text-center">
            <Music2 size={24} className="text-[var(--ink-dim)]" />
            <p className="text-[13px] text-[var(--ink-dim)]">Rien en lecture pour l&apos;instant.</p>
          </div>
        )}
      </div>

      {track && (
        <div className="glass flex min-h-0 flex-1 flex-col rounded-2xl p-4">
          <div className="mb-2 flex items-center gap-2 text-[var(--ink-soft)]">
            <ListMusic size={14} />
            <h2 className="text-[13px] font-bold uppercase tracking-wide">File d&apos;attente ({upcoming.length})</h2>
          </div>
          {upcoming.length === 0 ? (
            <p className="px-1 py-3 text-[13px] text-[var(--ink-dim)]">Fin de la file — rien après ce morceau.</p>
          ) : (
            <ul className="flex flex-col gap-0.5 overflow-y-auto">
              {upcoming.map(({ track: t, pos: p }) => (
                <li
                  key={`${p}-${t.kind}-${t.id}`}
                  onClick={() => jumpTo(p)}
                  className="flex cursor-pointer items-center gap-2.5 rounded-lg p-1.5 transition-colors hover:bg-white/[0.05]"
                >
                  <div className="art-fallback flex h-9 w-9 flex-shrink-0 items-center justify-center overflow-hidden rounded-md">
                    {t.thumbnailUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={t.thumbnailUrl} alt="" className="h-full w-full object-cover" />
                    ) : (
                      <Music2 size={13} className="text-white/25" />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[13px] font-semibold">{t.title}</div>
                    <div className="truncate text-[12px] text-[var(--ink-soft)]">{t.artist}</div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </aside>
  );
}
