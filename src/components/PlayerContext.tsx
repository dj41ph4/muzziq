"use client";

import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

/**
 * Lecteur global persistant (langage Spotify — mini-player qui survit à la
 * navigation, jamais un lecteur ré-instancié par page). Un seul élément
 * <audio> monté une fois dans le layout racine ; toute page déclenche la
 * lecture via `play()` au lieu de gérer son propre <audio> local.
 *
 * File d'attente (§player desktop) : `play(track, queue?)` accepte la liste
 * complète dans laquelle le morceau a été déclenché (carrousel, playlist,
 * bibliothèque…) pour permettre suivant/précédent/lecture aléatoire/répétition
 * réels — jamais de bouton qui ne fait qu'illustrer une fonctionnalité absente.
 */

export type RepeatMode = "off" | "all" | "one";

export interface PlayableTrack {
  kind: "local" | "offline" | "provider";
  /** fileId (local), id de téléchargement hors ligne (offline) ou providerTrackId (provider). */
  id: string;
  title: string;
  artist: string;
  album?: string;
  thumbnailUrl?: string;
  durationSeconds?: number;
  /** Recording MuzziQ d'origine, quand connu — permet d'agir sur le morceau (ex. téléchargement hors ligne) sans dépendre de kind/id qui varient selon la source résolue. */
  recordingId?: string;
}

interface PlayerState {
  track: PlayableTrack | null;
  isPlaying: boolean;
  isLoading: boolean;
  error: string | null;
  progress: number; // secondes
  duration: number; // secondes
  queue: PlayableTrack[];
  /** Ordre de lecture (indices dans `queue`) — mélangé si `shuffle`. */
  order: number[];
  /** Position courante dans `order`. */
  pos: number;
  shuffle: boolean;
  repeat: RepeatMode;
  volume: number;
}

interface PlayerContextValue extends PlayerState {
  play: (track: PlayableTrack, queue?: PlayableTrack[]) => void;
  togglePlay: () => void;
  seek: (seconds: number) => void;
  next: () => void;
  previous: () => void;
  /** Saute directement à une position de `order` (ex. clic sur un morceau de la file d'attente dans le panneau contextuel). */
  jumpTo: (pos: number) => void;
  toggleShuffle: () => void;
  cycleRepeat: () => void;
  setVolume: (v: number) => void;
  hasNext: boolean;
  hasPrevious: boolean;
}

const PlayerContext = createContext<PlayerContextValue | null>(null);

function sameTrack(a: PlayableTrack | null, b: PlayableTrack): boolean {
  return !!a && a.kind === b.kind && a.id === b.id;
}

function shuffled(indices: number[]): number[] {
  const arr = [...indices];
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

async function recordPlayStartEvent(track: PlayableTrack) {
  try {
    await fetch("/api/events", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        provider: track.kind === "provider" ? "youtube-music" : "local",
        providerTrackId: track.id,
        title: track.title,
        artist: track.artist,
        album: track.album,
        durationSeconds: track.durationSeconds,
        type: "PLAY_START",
        source: track.kind === "provider" ? "PROVIDER" : "LOCAL",
      }),
    });
  } catch {
    // L'historique ne doit jamais bloquer la lecture — best-effort.
  }
}

export function PlayerProvider({ children }: { children: ReactNode }) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [state, setState] = useState<PlayerState>({
    track: null,
    isPlaying: false,
    isLoading: false,
    error: null,
    progress: 0,
    duration: 0,
    queue: [],
    order: [],
    pos: 0,
    shuffle: false,
    repeat: "off",
    volume: 1,
  });
  // Reflète state.repeat pour le handler onEnded (fermé sur le premier render sinon).
  const repeatRef = useRef<RepeatMode>("off");
  // Miroir synchrone de `state` pour les actions déclenchées par clic (next/
  // previous) : évite d'appeler loadTrack (effets de bord : fetch, audio.play)
  // depuis l'intérieur d'un updater setState, qui peut être ré-invoqué par
  // StrictMode en dev et dupliquerait la lecture.
  const stateRef = useRef(state);
  stateRef.current = state;

  const loadTrack = useCallback((track: PlayableTrack) => {
    setState((s) => ({ ...s, track, isLoading: true, error: null, isPlaying: false, progress: 0, duration: 0 }));
    recordPlayStartEvent(track);

    (async () => {
      let url: string;
      if (track.kind === "local") {
        url = `/api/stream/${track.id}`;
      } else if (track.kind === "offline") {
        url = `/api/offline/${track.id}/stream`;
      } else {
        const res = await fetch(`/api/play/${track.id}`);
        const body = await res.json();
        if (!res.ok) {
          setState((s) => ({ ...s, isLoading: false, error: `${body.error} (${body.status})` }));
          return;
        }
        url = body.url;
      }
      const audio = audioRef.current;
      if (!audio) return;
      audio.src = url;
      audio.play().catch((err) => setState((s) => ({ ...s, isLoading: false, error: String(err) })));
    })();
  }, []);

  const play = useCallback(
    (track: PlayableTrack, queueTracks?: PlayableTrack[]) => {
      setState((s) => {
        const queue = queueTracks && queueTracks.length > 0 ? queueTracks : [track];
        let idx = queue.findIndex((t) => sameTrack(t, track));
        if (idx < 0) idx = 0;
        const order = s.shuffle ? shuffled(queue.map((_, i) => i)) : queue.map((_, i) => i);
        const pos = order.indexOf(idx);
        return { ...s, queue, order, pos: pos < 0 ? 0 : pos };
      });
      loadTrack(track);
    },
    [loadTrack]
  );

  const goTo = useCallback(
    (delta: number) => {
      const s = stateRef.current;
      if (s.queue.length === 0) return;
      let newPos = s.pos + delta;
      if (newPos < 0) newPos = s.repeat === "all" ? s.order.length - 1 : 0;
      if (newPos >= s.order.length) {
        if (s.repeat === "all") newPos = 0;
        else return; // fin de file, rien à faire
      }
      if (newPos === s.pos && s.queue.length > 1) return;
      const track = s.queue[s.order[newPos]];
      setState((prev) => ({ ...prev, pos: newPos }));
      loadTrack(track);
    },
    [loadTrack]
  );

  const next = useCallback(() => goTo(1), [goTo]);

  const jumpTo = useCallback(
    (pos: number) => {
      const s = stateRef.current;
      if (pos < 0 || pos >= s.order.length || pos === s.pos) return;
      const track = s.queue[s.order[pos]];
      setState((prev) => ({ ...prev, pos }));
      loadTrack(track);
    },
    [loadTrack]
  );

  const previous = useCallback(() => {
    const audio = audioRef.current;
    // Convention lecteur : si plus de 3s écoulées, "précédent" revient au début
    // du morceau courant plutôt que de sauter au précédent (§player desktop).
    if (audio && audio.currentTime > 3) {
      audio.currentTime = 0;
      return;
    }
    goTo(-1);
  }, [goTo]);

  const togglePlay = useCallback(() => {
    const audio = audioRef.current;
    if (!audio || !state.track) return;
    if (audio.paused) audio.play();
    else audio.pause();
  }, [state.track]);

  const seek = useCallback((seconds: number) => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.currentTime = seconds;
  }, []);

  const setVolume = useCallback((v: number) => {
    const clamped = Math.min(1, Math.max(0, v));
    const audio = audioRef.current;
    if (audio) audio.volume = clamped;
    setState((s) => ({ ...s, volume: clamped }));
  }, []);

  const toggleShuffle = useCallback(() => {
    setState((s) => {
      const nextShuffle = !s.shuffle;
      if (s.queue.length === 0) return { ...s, shuffle: nextShuffle };
      const currentIdx = s.order[s.pos];
      let order: number[];
      if (nextShuffle) {
        const rest = s.queue.map((_, i) => i).filter((i) => i !== currentIdx);
        order = [currentIdx, ...shuffled(rest)];
      } else {
        order = s.queue.map((_, i) => i);
      }
      return { ...s, shuffle: nextShuffle, order, pos: order.indexOf(currentIdx) };
    });
  }, []);

  const cycleRepeat = useCallback(() => {
    setState((s) => {
      const nextMode: RepeatMode = s.repeat === "off" ? "all" : s.repeat === "all" ? "one" : "off";
      repeatRef.current = nextMode;
      return { ...s, repeat: nextMode };
    });
  }, []);

  return (
    <PlayerContext.Provider
      value={{
        ...state,
        play,
        togglePlay,
        seek,
        next,
        previous,
        jumpTo,
        toggleShuffle,
        cycleRepeat,
        setVolume,
        hasNext: state.repeat !== "off" ? state.queue.length > 1 : state.pos < state.order.length - 1,
        hasPrevious: state.repeat !== "off" ? state.queue.length > 1 : state.pos > 0,
      }}
    >
      {children}
      {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
      <audio
        ref={audioRef}
        onPlaying={() => setState((s) => ({ ...s, isPlaying: true, isLoading: false }))}
        onPause={() => setState((s) => ({ ...s, isPlaying: false }))}
        onWaiting={() => setState((s) => ({ ...s, isLoading: true }))}
        // Lecture depuis audioRef.current plutôt que e.currentTarget : trouvé
        // en test réel (clic sur un morceau) — e.currentTarget peut être null
        // au moment où React traite l'event synthétique (élément retiré/
        // recréé pendant la transition de src), alors que la ref reste
        // stable tant que le composant est monté.
        onTimeUpdate={() => setState((s) => ({ ...s, progress: audioRef.current?.currentTime ?? 0 }))}
        onDurationChange={() => {
          const d = audioRef.current?.duration;
          setState((s) => ({ ...s, duration: Number.isFinite(d) ? (d as number) : 0 }));
        }}
        onError={() => setState((s) => ({ ...s, isLoading: false, error: "Erreur de lecture" }))}
        onEnded={() => {
          const audio = audioRef.current;
          if (repeatRef.current === "one" && audio) {
            audio.currentTime = 0;
            audio.play();
            return;
          }
          setState((s) => ({ ...s, isPlaying: false }));
          goTo(1);
        }}
      />
    </PlayerContext.Provider>
  );
}

export function usePlayer(): PlayerContextValue {
  const ctx = useContext(PlayerContext);
  if (!ctx) throw new Error("usePlayer doit être utilisé sous PlayerProvider");
  return ctx;
}
