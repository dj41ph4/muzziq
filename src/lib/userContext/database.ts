import fs from "node:fs";
import path from "node:path";
import type { DatabaseSync } from "node:sqlite";
import { DATA_DIR } from "@/lib/config";
import type { UserContextHealth } from "./types";

/**
 * Context Engine (porté depuis Movviz — "Context Engine" SQLite, additif et
 * résilient). Ledger d'événements par `user_id`, requêtable, qui alimente le
 * contexte injecté dans les prompts MuzziQ AI (plan §45) : historique
 * d'écoute réel, reprises en cours, affinités — jamais halluciné par le
 * modèle. Additif : les stores JSON existants (fsJsonCache) restent la
 * source de vérité pour tout le reste de l'app ; ce module n'ajoute qu'une
 * couche de requêtabilité pour l'IA, jamais un remplacement.
 *
 * Résilient : `MUZZIQ_CONTEXT_ENGINE_DISABLED=true` bascule immédiatement
 * `getUserContextDb()` sur `null` sans toucher au fichier — tout appelant
 * traite déjà un DB `null` comme "indisponible, dégrader proprement" (voir
 * `withUserContextDb`).
 */

const CONTEXT_DIR = path.join(DATA_DIR, "context");
export const USER_CONTEXT_DB_FILE = path.join(CONTEXT_DIR, "user-context.sqlite");
export const USER_CONTEXT_SCHEMA_VERSION = 1;

const g = globalThis as typeof globalThis & {
  __muzziqUserContextDb?: DatabaseSync | null;
  __muzziqUserContextDbPromise?: Promise<DatabaseSync | null>;
  __muzziqUserContextDbError?: string | null;
};

function isContextEngineDisabled(): boolean {
  return /^(?:1|true|yes|on)$/i.test((process.env.MUZZIQ_CONTEXT_ENGINE_DISABLED ?? "").trim());
}

function setError(error: unknown): void {
  g.__muzziqUserContextDbError = error instanceof Error ? error.message : String(error);
}

async function loadDatabaseSync(): Promise<(new (path: string) => DatabaseSync) | null> {
  try {
    // Trouvé en conditions réelles (2026-09-01) : le code serveur Next.js
    // tourne ici en ESM natif — ni `require` global (échoue avec "require is
    // not defined"), ni `createRequire(...)` fiable à travers le bundle
    // webpack ("runtimeRequire is not a function"). Seul un vrai `import()`
    // dynamique du module natif `node:sqlite` fonctionne dans ce contexte.
    const sqlite = (await import("node:sqlite")) as unknown as { DatabaseSync?: new (path: string) => DatabaseSync };
    return typeof sqlite.DatabaseSync === "function" ? sqlite.DatabaseSync : null;
  } catch (error) {
    setError(error);
    return null;
  }
}

function ensureSchema(db: DatabaseSync): void {
  db.exec(`
    PRAGMA journal_mode = WAL;
    PRAGMA synchronous = NORMAL;
    PRAGMA busy_timeout = 5000;
    PRAGMA foreign_keys = ON;

    CREATE TABLE IF NOT EXISTS context_schema (
      version INTEGER PRIMARY KEY,
      applied_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS context_events (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      event_type TEXT NOT NULL,
      source TEXT NOT NULL,
      recording_id TEXT,
      artist_snapshot TEXT,
      title_snapshot TEXT,
      album_snapshot TEXT,
      position_ms INTEGER,
      duration_ms INTEGER,
      numeric_value REAL,
      text_value TEXT,
      occurred_at INTEGER NOT NULL,
      recorded_at INTEGER NOT NULL,
      source_event_id TEXT,
      payload_json TEXT
    );

    CREATE INDEX IF NOT EXISTS idx_context_events_user_date
      ON context_events(user_id, occurred_at DESC);

    CREATE INDEX IF NOT EXISTS idx_context_events_user_type_date
      ON context_events(user_id, event_type, occurred_at DESC);

    CREATE INDEX IF NOT EXISTS idx_context_events_user_recording
      ON context_events(user_id, recording_id, occurred_at DESC);

    CREATE UNIQUE INDEX IF NOT EXISTS idx_context_events_source_event
      ON context_events(source, source_event_id)
      WHERE source_event_id IS NOT NULL;

    CREATE TABLE IF NOT EXISTS user_media_state (
      state_key TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      recording_id TEXT NOT NULL,
      artist_snapshot TEXT,
      title_snapshot TEXT,
      position_ms INTEGER,
      duration_ms INTEGER,
      progress_ratio REAL,
      eligible_for_resume INTEGER NOT NULL DEFAULT 0,
      completed INTEGER NOT NULL DEFAULT 0,
      play_count INTEGER NOT NULL DEFAULT 0,
      started_at INTEGER,
      last_played_at INTEGER,
      completed_at INTEGER,
      updated_at INTEGER NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_user_media_state_user_updated
      ON user_media_state(user_id, updated_at DESC);

    CREATE TABLE IF NOT EXISTS user_preferences (
      user_id TEXT NOT NULL,
      dimension TEXT NOT NULL,
      pref_key TEXT NOT NULL,
      label TEXT NOT NULL,
      affinity REAL NOT NULL,
      confidence REAL NOT NULL,
      source TEXT NOT NULL,
      evidence_count INTEGER NOT NULL DEFAULT 1,
      updated_at INTEGER NOT NULL,
      PRIMARY KEY(user_id, dimension, pref_key)
    );

    CREATE INDEX IF NOT EXISTS idx_user_preferences_user_updated
      ON user_preferences(user_id, updated_at DESC);
  `);

  const existing = db.prepare("SELECT version FROM context_schema WHERE version = ?").get(USER_CONTEXT_SCHEMA_VERSION);
  if (!existing) {
    db.prepare("INSERT INTO context_schema(version, applied_at) VALUES(?, ?)").run(USER_CONTEXT_SCHEMA_VERSION, Date.now());
  }
}

// Une seule tentative d'initialisation par process, mémorisée sur globalThis
// (Next.js compile chaque route en bundle séparé — une variable de module
// normale n'aurait pas été partagée entre elles). Bug réel trouvé en test :
// une première version marquait "initialisé" via un booléen posé AVANT que
// la promesse ne se résolve — des appels concurrents (Promise.all sur
// plusieurs requêtes SQL dans le même GET) lisaient alors g.__muzziqUserContextDb
// encore undefined et recevaient `null` au lieu d'attendre la vraie
// connexion. Mémoriser la PROMESSE elle-même élimine la fenêtre de course :
// tout appel concurrent attend la même promesse jusqu'à sa résolution.
export function getUserContextDb(): Promise<DatabaseSync | null> {
  if (g.__muzziqUserContextDbPromise) return g.__muzziqUserContextDbPromise;

  g.__muzziqUserContextDbPromise = (async () => {
    if (isContextEngineDisabled()) {
      g.__muzziqUserContextDbError = null;
      return null;
    }

    const Database = await loadDatabaseSync();
    if (!Database) return null;

    try {
      fs.mkdirSync(CONTEXT_DIR, { recursive: true });
      const db = new Database(USER_CONTEXT_DB_FILE);
      ensureSchema(db);
      g.__muzziqUserContextDb = db;
      g.__muzziqUserContextDbError = null;
      return db;
    } catch (error) {
      setError(error);
      return null;
    }
  })();

  return g.__muzziqUserContextDbPromise;
}

export async function withUserContextDb<T>(fn: (db: DatabaseSync) => T, fallback: T): Promise<T> {
  const db = await getUserContextDb();
  if (!db) return fallback;
  try {
    return fn(db);
  } catch (error) {
    setError(error);
    return fallback;
  }
}

export async function getUserContextHealth(): Promise<UserContextHealth> {
  const db = await getUserContextDb();
  return {
    database: db ? "ok" : g.__muzziqUserContextDbError ? "error" : "unavailable",
    schemaVersion: USER_CONTEXT_SCHEMA_VERSION,
    file: USER_CONTEXT_DB_FILE,
    lastError: g.__muzziqUserContextDbError ?? null,
  };
}
