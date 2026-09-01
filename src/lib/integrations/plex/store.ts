import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";

/** Config Plex (plan §53/§54) — intégration optionnelle, jamais une dépendance (§2, INTERDIT 1). */

const FILE = dataFile("plex-config.json");

export type PlexSyncPolicy = "OFF" | "IMPORT_ONLY" | "EXPORT_ONLY" | "BIDIRECTIONAL";

export interface PlexConfig {
  serverUrl: string;
  token: string;
  syncPolicy: PlexSyncPolicy;
  lastTest?: { ok: boolean; at: number; detail: string };
}

const DEFAULT: PlexConfig = { serverUrl: "", token: "", syncPolicy: "OFF" };

export function getPlexConfig(): PlexConfig {
  return { ...DEFAULT, ...readJsonCached<Partial<PlexConfig>>(FILE, {}) };
}

export function updatePlexConfig(patch: Partial<PlexConfig>): PlexConfig {
  const next = { ...getPlexConfig(), ...patch };
  writeJsonCached(FILE, next);
  return next;
}
