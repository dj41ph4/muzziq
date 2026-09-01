export type UserContextEventType =
  | "PLAY_START"
  | "PLAY_RESUMED"
  | "PLAY_STOPPED"
  | "PLAY_COMPLETE"
  | "SKIP"
  | "LIKE"
  | "DISLIKE"
  | "ADDED_TO_LIBRARY"
  | "RECOMMENDATION_ACCEPTED";

export interface UserContextEvent {
  id?: string;
  userId: string;
  eventType: UserContextEventType;
  source: string;
  sourceEventId?: string | null;
  recordingId?: string | null;
  artist?: string | null;
  title?: string | null;
  album?: string | null;
  positionMs?: number | null;
  durationMs?: number | null;
  numericValue?: number | null;
  textValue?: string | null;
  occurredAt: number;
  payload?: Record<string, unknown> | null;
}

export interface ContextMediaState {
  stateKey: string;
  userId: string;
  recordingId: string;
  artist?: string | null;
  title?: string | null;
  positionMs?: number | null;
  durationMs?: number | null;
  progressRatio?: number | null;
  eligibleForResume: boolean;
  completed: boolean;
  playCount: number;
  startedAt?: number | null;
  lastPlayedAt?: number | null;
  completedAt?: number | null;
  updatedAt: number;
}

export interface RecentPlayedContextItem {
  recordingId: string;
  title: string;
  artist: string;
  playedAt: number;
}

export interface CurrentlyResumableContextItem {
  recordingId: string;
  title: string;
  artist: string;
  positionMs: number;
  durationMs: number;
  progressRatio: number;
  lastPlayedAt: number | null;
}

export interface UnifiedUserContextSnapshot {
  recentPlayed: RecentPlayedContextItem[];
  resumable: CurrentlyResumableContextItem[];
  topArtists: { label: string; affinity: number; evidenceCount: number }[];
  generatedAt: number;
  storageAvailable: boolean;
}

export interface UserContextHealth {
  database: "ok" | "unavailable" | "error";
  schemaVersion: number;
  file: string;
  lastError: string | null;
}

/** Utilisateur unique tant qu'aucun système d'auth multi-utilisateur n'existe (plan Phase A, pas encore construite). */
export const DEFAULT_USER_ID = "local";
