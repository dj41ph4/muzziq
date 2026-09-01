import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";

/**
 * First real store — deliberately small. Demonstrates the pattern every
 * future MUZZIK store must follow (see architecture doc §105.1/§105.9):
 * never touch fs directly, always go through readJsonCached/writeJsonCached.
 */

const FILE = dataFile("settings.json");

export interface MuzzikSettings {
  serverName: string;
  /** Optional — MUZZIK must boot and be fully usable with this unset (architecture doc §2). */
  musicDir: string | null;
}

const DEFAULT: MuzzikSettings = {
  serverName: "MUZZIK",
  musicDir: null,
};

export function getSettings(): MuzzikSettings {
  return { ...DEFAULT, ...readJsonCached<Partial<MuzzikSettings>>(FILE, {}) };
}

export function updateSettings(patch: Partial<MuzzikSettings>): MuzzikSettings {
  const next = { ...getSettings(), ...patch };
  writeJsonCached(FILE, next);
  return next;
}
