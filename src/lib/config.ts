import path from "node:path";

/** Root directory for every MUZZIK JSON store, config file, and cache — never the music library itself. */
export const DATA_DIR =
  process.env.MUZZIK_CONFIG_DIR ?? process.env.MUZZIK_DATA_DIR ?? path.join(process.cwd(), ".muzzik-data");

export function dataFile(...segments: string[]): string {
  return path.join(DATA_DIR, ...segments);
}
