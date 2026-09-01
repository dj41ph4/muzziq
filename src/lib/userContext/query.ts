import { withUserContextDb, getUserContextHealth } from "./database";
import { getTopArtistAffinities } from "./preferences";
import type { CurrentlyResumableContextItem, RecentPlayedContextItem, UnifiedUserContextSnapshot } from "./types";

function clampLimit(limit: number, max: number): number {
  return Math.max(1, Math.min(max, Math.round(limit || 1)));
}

export async function getRecentPlayedContext(userId: string, limit = 15): Promise<RecentPlayedContextItem[]> {
  const max = clampLimit(limit, 100);
  return withUserContextDb((db) => {
    const rows = db
      .prepare(
        `SELECT recording_id, title_snapshot, artist_snapshot, occurred_at
         FROM context_events
         WHERE user_id = ? AND recording_id IS NOT NULL AND event_type IN ('PLAY_START', 'PLAY_COMPLETE')
         ORDER BY occurred_at DESC LIMIT ?`
      )
      .all(userId, max) as { recording_id: string; title_snapshot: string | null; artist_snapshot: string | null; occurred_at: number }[];
    return rows.map((r) => ({
      recordingId: r.recording_id,
      title: r.title_snapshot ?? "?",
      artist: r.artist_snapshot ?? "?",
      playedAt: r.occurred_at,
    }));
  }, []);
}

export async function getResumableContext(userId: string, limit = 8): Promise<CurrentlyResumableContextItem[]> {
  const max = clampLimit(limit, 20);
  return withUserContextDb((db) => {
    const rows = db
      .prepare(
        `SELECT recording_id, title_snapshot, artist_snapshot, position_ms, duration_ms, progress_ratio, last_played_at
         FROM user_media_state
         WHERE user_id = ? AND eligible_for_resume = 1 AND completed = 0
         ORDER BY last_played_at DESC LIMIT ?`
      )
      .all(userId, max) as {
      recording_id: string;
      title_snapshot: string | null;
      artist_snapshot: string | null;
      position_ms: number | null;
      duration_ms: number | null;
      progress_ratio: number | null;
      last_played_at: number | null;
    }[];
    return rows.map((r) => ({
      recordingId: r.recording_id,
      title: r.title_snapshot ?? "?",
      artist: r.artist_snapshot ?? "?",
      positionMs: r.position_ms ?? 0,
      durationMs: r.duration_ms ?? 0,
      progressRatio: r.progress_ratio ?? 0,
      lastPlayedAt: r.last_played_at,
    }));
  }, []);
}

export async function buildUnifiedUserContextSnapshot(userId: string): Promise<UnifiedUserContextSnapshot> {
  const [recentPlayed, resumable, topArtists, health] = await Promise.all([
    getRecentPlayedContext(userId, 15),
    getResumableContext(userId, 8),
    getTopArtistAffinities(userId, 5),
    getUserContextHealth(),
  ]);
  return {
    recentPlayed,
    resumable,
    topArtists: topArtists.map((a) => ({ label: a.artist, affinity: a.affinity, evidenceCount: a.evidenceCount })),
    generatedAt: Date.now(),
    storageAvailable: health.database === "ok",
  };
}

/**
 * Formate le snapshot en une chaîne compacte injectable dans le prompt
 * système de MUZZIK AI (plan §45 — "avant de dire qu'un morceau n'est pas
 * disponible, l'IA doit interroger MUZZIK") : jamais halluciné, toujours
 * dérivé du ledger réel.
 */
export function formatUnifiedUserContext(snapshot: UnifiedUserContextSnapshot): string {
  const parts: string[] = [];

  if (snapshot.resumable.length) {
    parts.push(
      `reprises en cours : ${snapshot.resumable
        .map((r) => `${r.title} — ${r.artist} (${Math.round(r.progressRatio * 100)}%)`)
        .join(" ; ")}`
    );
  }

  if (snapshot.topArtists.length) {
    parts.push(`artistes préférés (affinité observée) : ${snapshot.topArtists.map((a) => a.label).join(", ")}`);
  }

  if (snapshot.recentPlayed.length) {
    parts.push(
      `dernières écoutes : ${snapshot.recentPlayed
        .slice(0, 10)
        .map((r) => `${r.title} — ${r.artist} @${new Date(r.playedAt).toISOString()}`)
        .join(" ; ")}`
    );
  }

  return parts.join(" · ");
}
