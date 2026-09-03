import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";
import { randomUUID } from "node:crypto";

/**
 * Téléchargement hors ligne (indépendant de l'acquisition torrent, voir
 * `src/lib/acquisition/` pour ce sujet distinct). Un morceau peut être joué
 * via un provider dont l'URL de flux expire (YouTube Music) — ce store garde
 * la trace d'une copie locale volontairement téléchargée pour l'écoute hors
 * ligne, recordingId par recordingId, jamais un ID provider brut (INTERDIT 2
 * du plan d'architecture).
 *
 * Jamais de suppression automatique en cas d'échec réseau ponctuel — un
 * FAILED reste visible, pas supprimé silencieusement (même philosophie que
 * "missing" pour Plex, INTERDIT 8).
 */

const FILE = dataFile("offline-downloads.json");

export type OfflineDownloadState = "QUEUED" | "DOWNLOADING" | "COMPLETED" | "FAILED";

export interface OfflineDownload {
  id: string;
  recordingId: string;
  title: string;
  artist: string;
  album?: string;
  state: OfflineDownloadState;
  /** "local" = le morceau était déjà un MediaFile scanné (rien téléchargé, juste marqué) ; "provider" = flux réellement fetché et écrit sous DATA_DIR/offline. */
  sourceKind: "local" | "provider" | null;
  /**
   * Chemin absolu du fichier servi hors ligne. Pour sourceKind "local" c'est
   * le chemin du MediaFile existant (jamais copié, jamais supprimé par ce
   * store) ; pour "provider" c'est un fichier sous DATA_DIR/offline dont la
   * suppression de l'entrée doit aussi supprimer le fichier.
   */
  filePath: string | null;
  sizeBytes: number | null;
  error: string | null;
  createdAt: string;
  updatedAt: string;
}

function loadAll(): OfflineDownload[] {
  return readJsonCached<OfflineDownload[]>(FILE, []);
}

function saveAll(items: OfflineDownload[]): void {
  writeJsonCached(FILE, items);
}

export function listOfflineDownloads(): OfflineDownload[] {
  return loadAll();
}

export function getOfflineDownload(id: string): OfflineDownload | undefined {
  return loadAll().find((d) => d.id === id);
}

export function findOfflineDownloadByRecording(recordingId: string): OfflineDownload | undefined {
  return loadAll().find((d) => d.recordingId === recordingId);
}

/** Trouve un téléchargement COMPLETED réellement utilisable pour la lecture (priorité offline sur le réseau). */
export function findCompletedOfflineDownload(recordingId: string): OfflineDownload | undefined {
  return loadAll().find((d) => d.recordingId === recordingId && d.state === "COMPLETED" && d.filePath);
}

export function createOfflineDownload(input: {
  recordingId: string;
  title: string;
  artist: string;
  album?: string;
}): OfflineDownload {
  const now = new Date().toISOString();
  const record: OfflineDownload = {
    id: randomUUID(),
    recordingId: input.recordingId,
    title: input.title,
    artist: input.artist,
    album: input.album,
    state: "QUEUED",
    sourceKind: null,
    filePath: null,
    sizeBytes: null,
    error: null,
    createdAt: now,
    updatedAt: now,
  };
  const all = loadAll();
  all.push(record);
  saveAll(all);
  return record;
}

export function updateOfflineDownload(id: string, patch: Partial<OfflineDownload>): OfflineDownload | undefined {
  const all = loadAll();
  const idx = all.findIndex((d) => d.id === id);
  if (idx < 0) return undefined;
  const next: OfflineDownload = { ...all[idx], ...patch, id: all[idx].id, updatedAt: new Date().toISOString() };
  all[idx] = next;
  saveAll(all);
  return next;
}

export function removeOfflineDownload(id: string): boolean {
  const all = loadAll();
  const next = all.filter((d) => d.id !== id);
  if (next.length === all.length) return false;
  saveAll(next);
  return true;
}
