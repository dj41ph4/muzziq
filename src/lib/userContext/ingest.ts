import { randomUUID } from "node:crypto";
import { withUserContextDb } from "./database";
import type { ContextMediaState, UserContextEvent } from "./types";

/** Insertion append-only — idempotente par (source, sourceEventId) via l'index UNIQUE. */
export async function recordUserContextEvent(event: UserContextEvent): Promise<boolean> {
  return withUserContextDb((db) => {
    const result = db
      .prepare(
        `INSERT OR IGNORE INTO context_events(
          id, user_id, event_type, source, recording_id, artist_snapshot, title_snapshot, album_snapshot,
          position_ms, duration_ms, numeric_value, text_value, occurred_at, recorded_at, source_event_id, payload_json
        ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .run(
        event.id ?? randomUUID(),
        event.userId,
        event.eventType,
        event.source,
        event.recordingId ?? null,
        event.artist ?? null,
        event.title ?? null,
        event.album ?? null,
        event.positionMs ?? null,
        event.durationMs ?? null,
        event.numericValue ?? null,
        event.textValue ?? null,
        event.occurredAt,
        Date.now(),
        event.sourceEventId ?? null,
        event.payload ? JSON.stringify(event.payload) : null
      );
    return Number(result.changes) > 0;
  }, false);
}

export async function upsertUserMediaState(input: Omit<ContextMediaState, "stateKey"> & { stateKey?: string }): Promise<boolean> {
  const key = input.stateKey ?? `${input.userId}:${input.recordingId}`;
  return withUserContextDb((db) => {
    db.prepare(
      `INSERT INTO user_media_state(
        state_key, user_id, recording_id, artist_snapshot, title_snapshot,
        position_ms, duration_ms, progress_ratio, eligible_for_resume, completed, play_count,
        started_at, last_played_at, completed_at, updated_at
      ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(state_key) DO UPDATE SET
        artist_snapshot = COALESCE(excluded.artist_snapshot, user_media_state.artist_snapshot),
        title_snapshot = COALESCE(excluded.title_snapshot, user_media_state.title_snapshot),
        position_ms = COALESCE(excluded.position_ms, user_media_state.position_ms),
        duration_ms = COALESCE(excluded.duration_ms, user_media_state.duration_ms),
        progress_ratio = COALESCE(excluded.progress_ratio, user_media_state.progress_ratio),
        eligible_for_resume = excluded.eligible_for_resume,
        completed = excluded.completed,
        play_count = user_media_state.play_count + excluded.play_count,
        started_at = COALESCE(user_media_state.started_at, excluded.started_at),
        last_played_at = COALESCE(excluded.last_played_at, user_media_state.last_played_at),
        completed_at = COALESCE(excluded.completed_at, user_media_state.completed_at),
        updated_at = excluded.updated_at
      `
    ).run(
      key,
      input.userId,
      input.recordingId,
      input.artist ?? null,
      input.title ?? null,
      input.positionMs ?? null,
      input.durationMs ?? null,
      input.progressRatio ?? null,
      input.eligibleForResume ? 1 : 0,
      input.completed ? 1 : 0,
      input.playCount ?? 0,
      input.startedAt ?? null,
      input.lastPlayedAt ?? null,
      input.completedAt ?? null,
      input.updatedAt
    );
    return true;
  }, false);
}

/**
 * Point d'entrée unique appelé quand un morceau démarre — écrit à la fois
 * l'événement PLAY_START (ledger append-only) et l'état de reprise (upsert).
 * Additif : appelé EN PLUS de `recordEvent()` (store JSON existant,
 * `src/lib/history/playbackEventsStore.ts`), jamais à sa place — un échec
 * ici (DB désactivée/indisponible) ne doit jamais faire échouer la lecture.
 */
export async function recordPlaybackStartedContext(input: {
  userId: string;
  recordingId: string;
  title: string;
  artist: string;
  durationMs?: number;
}): Promise<void> {
  const now = Date.now();
  await upsertUserMediaState({
    userId: input.userId,
    recordingId: input.recordingId,
    artist: input.artist,
    title: input.title,
    positionMs: 0,
    durationMs: input.durationMs ?? null,
    progressRatio: 0,
    eligibleForResume: false,
    completed: false,
    playCount: 1,
    startedAt: now,
    lastPlayedAt: now,
    completedAt: null,
    updatedAt: now,
  });
  await recordUserContextEvent({
    userId: input.userId,
    eventType: "PLAY_START",
    source: "muzziq_playback",
    recordingId: input.recordingId,
    artist: input.artist,
    title: input.title,
    durationMs: input.durationMs ?? null,
    occurredAt: now,
  });
}
