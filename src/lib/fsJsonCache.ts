import fs from "node:fs";
import path from "node:path";

/**
 * Process-wide cache for the JSON files that back every MUZZIK store.
 *
 * Ported from Movviz (`src/lib/fsJsonCache.ts`) — same author, same problem
 * class (a file-backed JSON store re-read/re-parsed on every API call stalls
 * the process once the library grows). Two properties matter from day one:
 *
 * 1. Reads are cache-backed and revalidated with a single stat() call — if
 *    mtime/size haven't changed, the cached object is returned as-is.
 * 2. Writes are atomic (temp file + rename) and coalesced — a burst of writes
 *    to the same file within WRITE_COALESCE_MS collapses into one disk write
 *    of the final state, so a loop over many items never queues N full
 *    JSON.stringify's in memory.
 *
 * Anchored on globalThis because Next.js compiles routes into separate
 * bundles — module-level state would otherwise exist once per bundle.
 *
 * Deliberately left out for now (see architecture doc §105.3 — don't
 * pre-build complexity before a real volume need exists): the worker_threads
 * offload Movviz added once a store crossed ~1MB and stringify started
 * blocking the main thread for tens of ms. Add it here the same way, if and
 * when a MUZZIK store actually gets that large.
 */

interface CacheEntry {
  mtimeMs: number;
  size: number;
  value: unknown;
  /** Set while this value hasn't been confirmed on disk yet — reads trust it as-is instead of re-validating against a stat() that wouldn't reflect it yet. */
  pending?: boolean;
}

const g = globalThis as typeof globalThis & {
  __muzzikFsJsonCache?: Map<string, CacheEntry>;
  __muzzikMemoCache?: Map<string, { version: string; value: unknown }>;
  __muzzikPendingWrites?: Map<string, { value: unknown; timer: ReturnType<typeof setTimeout> }>;
  __muzzikWriteInFlight?: Map<string, boolean>;
  __muzzikPendingFileWrites?: Map<string, unknown>;
  __muzzikJsonReadFailures?: Set<string>;
};

const cache: Map<string, CacheEntry> = (g.__muzzikFsJsonCache ??= new Map());
const memoCache: Map<string, { version: string; value: unknown }> = (g.__muzzikMemoCache ??= new Map());
const pendingWrites: Map<string, { value: unknown; timer: ReturnType<typeof setTimeout> }> =
  (g.__muzzikPendingWrites ??= new Map());
const writeInFlight: Map<string, boolean> = (g.__muzzikWriteInFlight ??= new Map());
const pendingFileWrites: Map<string, unknown> = (g.__muzzikPendingFileWrites ??= new Map());
const readFailures: Set<string> = (g.__muzzikJsonReadFailures ??= new Set());

const WRITE_COALESCE_MS = 300;

/** True if the last read of `file` hit a parse error instead of the file being absent. */
export function jsonCacheReadFailed(file: string): boolean {
  return readFailures.has(file);
}

export function readJsonCached<T>(file: string, fallback: T): T {
  const hit = cache.get(file);
  if (hit?.pending) {
    readFailures.delete(file);
    return hit.value as T;
  }
  let stat: fs.Stats;
  try {
    stat = fs.statSync(file);
  } catch {
    return fallback;
  }
  if (hit && hit.mtimeMs === stat.mtimeMs && hit.size === stat.size) {
    readFailures.delete(file);
    return hit.value as T;
  }
  try {
    const value = JSON.parse(fs.readFileSync(file, "utf8")) as T;
    cache.set(file, { mtimeMs: stat.mtimeMs, size: stat.size, value });
    readFailures.delete(file);
    return value;
  } catch {
    // Parse/read failure ≠ file absent. Callers must never write the
    // fallback back over a file that merely failed to read — that would
    // silently wipe real data. Use jsonCacheReadFailed() to guard writes.
    readFailures.add(file);
    return fallback;
  }
}

/**
 * Cache-aware write for a store backed by readJsonCached. The in-memory
 * cache updates synchronously — every read in this process sees the new
 * value immediately — while the actual disk write (temp file + rename,
 * atomic against a mid-write crash) happens in the background. Writes to the
 * same file are serialized and coalesced: a burst of calls in quick
 * succession collapses into one disk write of the final state.
 */
export function writeJsonCached(file: string, value: unknown): void {
  cache.set(file, { mtimeMs: -1, size: -1, value, pending: true });
  readFailures.delete(file);

  const existing = pendingWrites.get(file);
  if (existing) {
    existing.value = value;
    return;
  }

  const timer = setTimeout(() => {
    const pending = pendingWrites.get(file);
    pendingWrites.delete(file);
    startFileWrite(file, pending ? pending.value : value);
  }, WRITE_COALESCE_MS);

  pendingWrites.set(file, { value, timer });
}

function startFileWrite(file: string, val: unknown) {
  if (writeInFlight.get(file)) {
    pendingFileWrites.set(file, val);
    return;
  }
  writeInFlight.set(file, true);

  const json = JSON.stringify(val, null, 2);
  const tmp = `${file}.tmp`;

  // Bug réel trouvé en test (2026-09-01) : le premier write d'un store
  // JAMAIS écrit avant échouait silencieusement (ENOENT, avalé par le
  // .catch plus bas) parce que le dossier .muzzik-data n'existe pas encore.
  // Les lectures suivantes semblaient marcher (servies par le cache mémoire
  // `pending`), masquant totalement l'échec — rien n'était jamais persisté
  // sur disque. mkdir recursive avant chaque write neutralise la classe
  // entière du problème, pas seulement ce cas précis.
  fs.promises
    .mkdir(path.dirname(file), { recursive: true })
    .then(() => fs.promises.writeFile(tmp, json, "utf8"))
    .then(() => fs.promises.rename(tmp, file))
    .then(() => fs.promises.stat(file))
    .then((stat) => {
      const current = cache.get(file);
      if (current?.value === val) {
        cache.set(file, { mtimeMs: stat.mtimeMs, size: stat.size, value: val });
      }
    })
    .catch((err: unknown) => {
      console.error(`[fsJsonCache] background write failed for ${file}:`, err);
    })
    .finally(() => {
      writeInFlight.set(file, false);
      const next = pendingFileWrites.get(file);
      if (next !== undefined) {
        pendingFileWrites.delete(file);
        startFileWrite(file, next);
      }
    });
}

/** Purges every in-memory cache. Call BEFORE deleting store files, never after — otherwise a pending write can recreate what you just deleted. */
export function resetAllCaches(): void {
  for (const { timer } of pendingWrites.values()) clearTimeout(timer);
  pendingWrites.clear();
  cache.clear();
  readFailures.clear();
  memoCache.clear();
  writeInFlight.clear();
  pendingFileWrites.clear();
}

/**
 * Memoize an expensive derived computation keyed by the mtime/size of the
 * source files it reads — cheap on every call (a handful of statSync), only
 * re-runs `compute` once the underlying data actually changed.
 */
export function memoizeByFileMtimes<T>(key: string, files: string[], compute: () => T): T {
  const version = files
    .map((f) => {
      try {
        const s = fs.statSync(f);
        return `${s.mtimeMs}:${s.size}:${cache.get(f)?.pending ? "pending" : "disk"}`;
      } catch {
        return "missing";
      }
    })
    .join("|");
  const hit = memoCache.get(key);
  if (hit && hit.version === version) return hit.value as T;
  const value = compute();
  memoCache.set(key, { version, value });
  return value;
}
