"use client";

import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

/**
 * Lecteur global persistant (langage Spotify — mini-player qui survit à la
 * navigation, jamais un lecteur ré-instancié par page). Un seul élément
 * <audio> monté une fois dans le layout racine ; toute page déclenche la
 * lecture via `play()` au lieu de gérer son propre <audio> local.
 */

export interface PlayableTrack {
  kind: "local" | "provider";
  /** fileId (local) ou providerTrackId (provider). */
  id: string;
  title: string;
  artist: string;
  album?: string;
  thumbnailUrl?: string;
  durationSeconds?: number;
}

interface PlayerState {
  track: PlayableTrack | null;
  isPlaying: boolean;
  isLoading: boolean;
  error: string | null;
  progress: number; // secondes
  duration: number; // secondes
}

interface PlayerContextValue extends PlayerState {
  play: (track: PlayableTrack) => void;
  togglePlay: () => void;
  seek: (seconds: number) => void;
}

const PlayerContext = createContext<PlayerContextValue | null>(null);

async function recordPlayStartEvent(track: PlayableTrack) {
  try {
    await fetch("/api/events", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        provider: track.kind === "local" ? "local" : "youtube-music",
        providerTrackId: track.id,
        title: track.title,
        artist: track.artist,
        album: track.album,
        durationSeconds: track.durationSeconds,
        type: "PLAY_START",
        source: track.kind === "local" ? "LOCAL" : "PROVIDER",
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
  });

  const play = useCallback(async (track: PlayableTrack) => {
    setState((s) => ({ ...s, track, isLoading: true, error: null, isPlaying: false, progress: 0 }));
    recordPlayStartEvent(track);

    let url: string;
    if (track.kind === "local") {
      url = `/api/stream/${track.id}`;
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
  }, []);

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

  return (
    <PlayerContext.Provider value={{ ...state, play, togglePlay, seek }}>
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
        onEnded={() => setState((s) => ({ ...s, isPlaying: false }))}
      />
    </PlayerContext.Provider>
  );
}

export function usePlayer(): PlayerContextValue {
  const ctx = useContext(PlayerContext);
  if (!ctx) throw new Error("usePlayer doit être utilisé sous PlayerProvider");
  return ctx;
}
