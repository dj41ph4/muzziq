import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";

const FILE = dataFile("favorites.json");

export interface FavoriteEntry {
  recordingId: string;
  addedAt: string;
}

function loadAll(): FavoriteEntry[] {
  return readJsonCached<FavoriteEntry[]>(FILE, []);
}

function saveAll(items: FavoriteEntry[]): void {
  writeJsonCached(FILE, items);
}

export function listFavorites(): FavoriteEntry[] {
  return [...loadAll()].sort((a, b) => b.addedAt.localeCompare(a.addedAt));
}

export function isFavorite(recordingId: string): boolean {
  return loadAll().some((item) => item.recordingId === recordingId);
}

export function addFavorite(recordingId: string): FavoriteEntry {
  const existing = loadAll().find((item) => item.recordingId === recordingId);
  if (existing) return existing;
  const item = { recordingId, addedAt: new Date().toISOString() };
  saveAll([...loadAll(), item]);
  return item;
}

export function removeFavorite(recordingId: string): boolean {
  const all = loadAll();
  const next = all.filter((item) => item.recordingId !== recordingId);
  if (next.length === all.length) return false;
  saveAll(next);
  return true;
}
