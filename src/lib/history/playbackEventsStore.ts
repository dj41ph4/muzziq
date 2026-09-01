import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";
import { randomUUID } from "node:crypto";

/**
 * Historique d'écoute (plan §41) : des événements, jamais "play lancé = écouté".
 * Store en anneau borné + append — un journal simple suffit tant que le
 * volume reste modeste (plan §105.9 — pattern journal borné à généraliser
 * si un store d'événements devient volumineux).
 */

const FILE = dataFile("playback-events.json");
const MAX_EVENTS = 5000;

export type PlaybackEventType = "PLAY_START" | "PLAY_COMPLETE" | "SKIP";

export interface PlaybackEvent {
  id: string;
  recordingId: string;
  type: PlaybackEventType;
  source: "LOCAL" | "PROVIDER";
  at: string;
}

function loadAll(): PlaybackEvent[] {
  return readJsonCached<PlaybackEvent[]>(FILE, []);
}

export function recordEvent(input: Omit<PlaybackEvent, "id" | "at">): PlaybackEvent {
  const event: PlaybackEvent = { ...input, id: randomUUID(), at: new Date().toISOString() };
  const all = loadAll();
  all.push(event);
  const trimmed = all.length > MAX_EVENTS ? all.slice(all.length - MAX_EVENTS) : all;
  writeJsonCached(FILE, trimmed);
  return event;
}

export function listRecentEvents(limit = 50): PlaybackEvent[] {
  return loadAll().slice(-limit).reverse();
}
