import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";

/**
 * First real store — deliberately small. Demonstrates the pattern every
 * future MuzziQ store must follow (see architecture doc §105.1/§105.9):
 * never touch fs directly, always go through readJsonCached/writeJsonCached.
 */

const FILE = dataFile("settings.json");

export interface MuzziQSettings {
  serverName: string;
  /** Optional — MuzziQ must boot and be fully usable with this unset (architecture doc §2). */
  musicDir: string | null;
}

const DEFAULT: MuzziQSettings = {
  serverName: "MuzziQ",
  musicDir: null,
};

export function getSettings(): MuzziQSettings {
  return { ...DEFAULT, ...readJsonCached<Partial<MuzziQSettings>>(FILE, {}) };
}

export function updateSettings(patch: Partial<MuzziQSettings>): MuzziQSettings {
  const next = { ...getSettings(), ...patch };
  writeJsonCached(FILE, next);
  return next;
}
