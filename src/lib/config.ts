import path from "node:path";

/** Root directory for every MuzziQ JSON store, config file, and cache — never the music library itself. */
export const DATA_DIR =
  process.env.MUZZIQ_CONFIG_DIR ?? process.env.MUZZIQ_DATA_DIR ?? path.join(process.cwd(), ".muzziq-data");

export function dataFile(...segments: string[]): string {
  return path.join(DATA_DIR, ...segments);
}
