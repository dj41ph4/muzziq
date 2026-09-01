import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";
import { randomUUID } from "node:crypto";

/**
 * Recording (plan §6/§8) : l'enregistrement conceptuel — jamais un ID
 * provider. Version V1 volontairement plate (pas encore de distinction
 * Track/Recording par édition — plan §8 ; viendra avec AlbumEdition, pas
 * avant qu'un vrai besoin de désambiguïsation d'édition se présente).
 */

const FILE = dataFile("recordings.json");

export interface Recording {
  id: string;
  title: string;
  artist: string;
  album?: string;
  durationSeconds?: number;
  thumbnailUrl?: string;
  createdAt: string;
}

function loadAll(): Recording[] {
  return readJsonCached<Recording[]>(FILE, []);
}

function saveAll(records: Recording[]): void {
  writeJsonCached(FILE, records);
}

export function listRecordings(): Recording[] {
  return loadAll();
}

export function getRecording(id: string): Recording | undefined {
  return loadAll().find((r) => r.id === id);
}

export function createRecording(input: Omit<Recording, "id" | "createdAt">): Recording {
  const record: Recording = { ...input, id: randomUUID(), createdAt: new Date().toISOString() };
  const all = loadAll();
  all.push(record);
  saveAll(all);
  return record;
}
