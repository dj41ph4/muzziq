import { randomUUID } from "node:crypto";
import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";
import type { PlexPathMapping } from "./types";

/** Config Plex (plan §53/§54) — intégration optionnelle, jamais une dépendance (§2, INTERDIT 1). */

const FILE = dataFile("plex-config.json");

export type PlexSyncPolicy = "OFF" | "IMPORT_ONLY" | "EXPORT_ONLY" | "BIDIRECTIONAL";

export interface PlexSyncReport {
  at: number;
  ok: boolean;
  summary: string;
}

export interface PlexConfig {
  /** URL du serveur choisie parmi les connexions renvoyées par resources.plex.tv (ou saisie à la main). */
  serverUrl: string;
  /** Token utilisé pour parler AU SERVEUR (le token de compte Plex marche directement sur un serveur qu'on possède). */
  token: string;
  syncPolicy: PlexSyncPolicy;
  lastTest?: { ok: boolean; at: number; detail: string };

  /** Identifiant client stable, requis par le flow OAuth PIN Plex — miné une seule fois, jamais régénéré. */
  clientId: string;
  /** Token de compte plex.tv obtenu via OAuth (distinct de `token` : sert à relister les serveurs/comptes, jamais envoyé à un serveur tiers). */
  accountToken?: string;
  accountUsername?: string;

  machineIdentifier?: string;
  serverName?: string;

  /** Sections Plex de type "artist" sélectionnées pour la synchronisation musicale. */
  musicSections: { key: string; title: string }[];

  /** Mapping de chemins Plex → MuzziQ (cas NAS avec points de montage différents). */
  pathMappings: PlexPathMapping[];

  lastLibrarySync?: PlexSyncReport;
  lastPlaylistSync?: PlexSyncReport;
  /** Horodatage (secondes unix) du dernier événement d'historique importé — sert de curseur incrémental. */
  lastHistoryWatermark?: number;
  lastHistorySync?: PlexSyncReport;
}

const DEFAULT: PlexConfig = {
  serverUrl: "",
  token: "",
  syncPolicy: "OFF",
  clientId: "",
  musicSections: [],
  pathMappings: [],
};

export function getPlexConfig(): PlexConfig {
  const cfg: PlexConfig = { ...DEFAULT, ...readJsonCached<Partial<PlexConfig>>(FILE, {}) };
  if (!cfg.musicSections) cfg.musicSections = [];
  if (!cfg.pathMappings) cfg.pathMappings = [];
  // Chaque requête OAuth Plex a besoin d'un identifiant client stable — miné une fois, conservé pour toujours.
  if (!cfg.clientId) {
    cfg.clientId = randomUUID();
    writeJsonCached(FILE, cfg);
  }
  return cfg;
}

export function updatePlexConfig(patch: Partial<PlexConfig>): PlexConfig {
  const next = { ...getPlexConfig(), ...patch };
  writeJsonCached(FILE, next);
  return next;
}

export function plexIsConnected(config: PlexConfig = getPlexConfig()): boolean {
  return !!config.serverUrl && !!config.token;
}
