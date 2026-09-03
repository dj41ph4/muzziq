import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";
import { randomUUID } from "node:crypto";

/**
 * Store des fichiers audio locaux découverts par le scanner (plan §34).
 * La DB MuzziQ fait foi pour l'état applicatif, mais ne doit jamais inventer
 * la présence d'un fichier (plan §35) — la réconciliation (mtime/size vs
 * disque réel) est faite par le scanner, pas ici.
 */

const FILE = dataFile("media-files.json");

export interface MediaFile {
  id: string;
  /** Chemin absolu réel sur disque — jamais dérivé d'une entrée utilisateur, uniquement écrit par le scanner. */
  path: string;
  title: string;
  artist: string;
  album?: string;
  trackNumber?: number;
  durationSeconds?: number;
  /** Code ISRC lu depuis les tags du fichier (TSRC/ID3, ISRC/Vorbis), quand présent — signal de matching quasi certain (plan §48/§104). */
  isrc?: string;
  codec?: string;
  sampleRate?: number;
  bitsPerSample?: number;
  container: string;
  sizeBytes: number;
  mtimeMs: number;
  scannedAt: string;
}

function loadAll(): MediaFile[] {
  return readJsonCached<MediaFile[]>(FILE, []);
}

function saveAll(files: MediaFile[]): void {
  writeJsonCached(FILE, files);
}

export function listMediaFiles(): MediaFile[] {
  return loadAll();
}

export function getMediaFile(id: string): MediaFile | undefined {
  return loadAll().find((f) => f.id === id);
}

/** Insère ou met à jour (par chemin) une entrée découverte par le scanner. */
export function upsertMediaFile(entry: Omit<MediaFile, "id" | "scannedAt">): MediaFile {
  const all = loadAll();
  const existingIndex = all.findIndex((f) => f.path === entry.path);
  const record: MediaFile = {
    ...entry,
    id: existingIndex >= 0 ? all[existingIndex].id : randomUUID(),
    scannedAt: new Date().toISOString(),
  };
  if (existingIndex >= 0) {
    all[existingIndex] = record;
  } else {
    all.push(record);
  }
  saveAll(all);
  return record;
}

/** Retire les entrées dont le chemin n'a pas été revu lors du dernier scan complet (fichier disparu du disque). */
export function pruneMissing(seenPaths: Set<string>): number {
  const all = loadAll();
  const kept = all.filter((f) => seenPaths.has(f.path));
  saveAll(kept);
  return all.length - kept.length;
}
