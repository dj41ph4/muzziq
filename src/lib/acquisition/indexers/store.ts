import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";
import { randomUUID } from "node:crypto";
import type { ConfiguredIndexer } from "./types";

const FILE = dataFile("indexers.json");

function loadAll(): ConfiguredIndexer[] {
  return readJsonCached<ConfiguredIndexer[]>(FILE, []);
}
function saveAll(items: ConfiguredIndexer[]): void {
  writeJsonCached(FILE, items);
}

export function listIndexers(): ConfiguredIndexer[] {
  return loadAll();
}

export function getIndexer(id: string): ConfiguredIndexer | undefined {
  return loadAll().find((i) => i.id === id);
}

export function addIndexer(input: Omit<ConfiguredIndexer, "id" | "addedAt" | "priority">): ConfiguredIndexer {
  const all = loadAll();
  const indexer: ConfiguredIndexer = { ...input, id: randomUUID(), addedAt: Date.now(), priority: all.length + 1 };
  all.push(indexer);
  saveAll(all);
  return indexer;
}

export function updateIndexer(id: string, patch: Partial<ConfiguredIndexer>): ConfiguredIndexer | undefined {
  const all = loadAll();
  const idx = all.findIndex((i) => i.id === id);
  if (idx < 0) return undefined;
  all[idx] = { ...all[idx], ...patch };
  saveAll(all);
  return all[idx];
}

export function removeIndexer(id: string): boolean {
  const all = loadAll();
  const next = all.filter((i) => i.id !== id);
  if (next.length === all.length) return false;
  saveAll(next);
  return true;
}
