import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";
import { randomUUID } from "node:crypto";

/**
 * LibraryItem (plan §18/§48). "+ Bibliothèque" ne veut PAS dire télécharger —
 * addPolicy décide. STREAM_ONLY par défaut : on mémorise, on ne lance rien.
 */

const FILE = dataFile("library-items.json");

export type AddPolicy = "STREAM_ONLY" | "MONITOR" | "ACQUIRE_IMMEDIATELY";

export interface LibraryItem {
  id: string;
  type: "TRACK";
  recordingId: string;
  monitored: boolean;
  addPolicy: AddPolicy;
  addedAt: string;
}

function loadAll(): LibraryItem[] {
  return readJsonCached<LibraryItem[]>(FILE, []);
}

function saveAll(items: LibraryItem[]): void {
  writeJsonCached(FILE, items);
}

export function listLibraryItems(): LibraryItem[] {
  return loadAll();
}

export function findByRecordingId(recordingId: string): LibraryItem | undefined {
  return loadAll().find((i) => i.recordingId === recordingId);
}

export function addLibraryItem(recordingId: string, addPolicy: AddPolicy = "STREAM_ONLY"): LibraryItem {
  const existing = findByRecordingId(recordingId);
  if (existing) return existing;

  const item: LibraryItem = {
    id: randomUUID(),
    type: "TRACK",
    recordingId,
    monitored: addPolicy !== "STREAM_ONLY",
    addPolicy,
    addedAt: new Date().toISOString(),
  };
  const all = loadAll();
  all.push(item);
  saveAll(all);
  return item;
}

export function removeLibraryItem(id: string): boolean {
  const all = loadAll();
  const next = all.filter((i) => i.id !== id);
  if (next.length === all.length) return false;
  saveAll(next);
  return true;
}
