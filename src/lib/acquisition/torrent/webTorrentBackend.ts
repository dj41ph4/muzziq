import path from "node:path";
import fsp from "node:fs/promises";
import { DATA_DIR } from "@/lib/config";

/**
 * TorrentBackend WebTorrent (plan §22) — réimplémentation indépendante et
 * volontairement réduite du AbstractBackend/WebTorrentBackend Movviz : même
 * API WebTorrent v2 réelle (options client.add, gestion du port, la
 * particularité "pause() n'arrête pas les pairs, seul remove() le fait"),
 * mais sans la logique de correspondance épisode/saison (spécifique vidéo,
 * MuzziQ n'en a pas besoin — l'import musical est géré séparément).
 *
 * Pas aria2 (INTERDIT 9). Process actuel : tourne dans le process Next.js
 * plutôt qu'un `engine/` séparé comme Movviz — simplification délibérée pour
 * ce premier bootstrap (§105.3, ne pas construire la complexité avant qu'un
 * besoin réel — perf, isolation crash — ne l'exige). Anchoring globalThis
 * (voir les règles opérationnelles du projet) pour survivre au découpage en bundles par route.
 */

export const DOWNLOAD_INCOMPLETE_DIR = path.join(DATA_DIR, "downloads", "incomplete");
export const DOWNLOAD_COMPLETE_DIR = path.join(DATA_DIR, "downloads", "complete");

export type TorrentState = "downloading" | "seeding" | "completed" | "paused" | "queued" | "error";

export interface TorrentSummary {
  infoHash: string;
  name: string;
  length: number;
  downloaded: number;
  progress: number;
  downloadSpeed: number;
  uploadSpeed: number;
  numPeers: number;
  state: TorrentState;
  addedAt: number;
  completedAt: number | null;
  files: { name: string; length: number }[];
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type WTClient = any;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type WTTorrent = any;

interface TorrentMeta {
  addedAt: number;
  completedAt: number | null;
  userPaused: boolean;
  onCompleteCalled: boolean;
}

const g = globalThis as typeof globalThis & {
  __muzziqWtClient?: WTClient | null;
  __muzziqWtClientPromise?: Promise<WTClient>;
  __muzziqWtMeta?: Map<string, TorrentMeta>;
};

function metaMap(): Map<string, TorrentMeta> {
  return (g.__muzziqWtMeta ??= new Map());
}

async function createClient(): Promise<WTClient> {
  const WebTorrent = (await import("webtorrent")).default;
  await fsp.mkdir(DOWNLOAD_INCOMPLETE_DIR, { recursive: true });

  return new Promise((resolve, reject) => {
    const client = new WebTorrent({ maxConns: 55, dht: true, utp: false, tracker: { wrtc: false } });
    const timer = setTimeout(() => resolve(client), 3000); // WebTorrent v2 n'émet pas toujours "listening" en DHT-only — un délai raisonnable suffit à laisser le client s'initialiser.
    client.once("error", (e: string | Error) => {
      clearTimeout(timer);
      reject(e);
    });
    client.once("listening", () => {
      clearTimeout(timer);
      resolve(client);
    });
  });
}

async function getClient(): Promise<WTClient> {
  if (g.__muzziqWtClient) return g.__muzziqWtClient;
  if (g.__muzziqWtClientPromise) return g.__muzziqWtClientPromise;
  g.__muzziqWtClientPromise = createClient().then((c) => {
    g.__muzziqWtClient = c;
    return c;
  });
  return g.__muzziqWtClientPromise;
}

function summarize(t: WTTorrent): TorrentSummary {
  const meta = metaMap().get(t.infoHash);
  const state: TorrentState = meta?.completedAt
    ? "completed"
    : meta?.userPaused
      ? "paused"
      : t.done
        ? "seeding"
        : "downloading";
  return {
    infoHash: t.infoHash,
    name: t.name ?? t.infoHash,
    length: t.length ?? 0,
    downloaded: t.downloaded ?? 0,
    progress: t.length > 0 ? (t.downloaded ?? 0) / t.length : 0,
    downloadSpeed: t.downloadSpeed ?? 0,
    uploadSpeed: t.uploadSpeed ?? 0,
    numPeers: t.numPeers ?? 0,
    state,
    addedAt: meta?.addedAt ?? Date.now(),
    completedAt: meta?.completedAt ?? null,
    files: (t.files ?? []).map((f: { name: string; length: number }) => ({ name: f.name, length: f.length ?? 0 })),
  };
}

export async function addTorrent(torrentId: string, onComplete: (t: WTTorrent) => void): Promise<TorrentSummary> {
  const client = await getClient();
  const existing = client.torrents.find((t: WTTorrent) => t.magnetURI === torrentId || t.infoHash === torrentId);
  if (existing) return summarize(existing);

  return new Promise((resolve, reject) => {
    const t = client.add(torrentId, { path: DOWNLOAD_INCOMPLETE_DIR });
    t.on("error", reject);
    let waited = 0;
    const settle = () => {
      if (t.infoHash) {
        metaMap().set(t.infoHash, { addedAt: Date.now(), completedAt: null, userPaused: false, onCompleteCalled: false });
        t.on("done", () => {
          const m = metaMap().get(t.infoHash);
          if (m && !m.onCompleteCalled) {
            m.onCompleteCalled = true;
            m.completedAt = Date.now();
            onComplete(t);
          }
        });
        resolve(summarize(t));
      } else if ((waited += 25) > 15000) {
        reject(new Error("infoHash non résolu (torrent invalide ou trackers injoignables)"));
      } else {
        setTimeout(settle, 25);
      }
    };
    settle();
  });
}

export async function listTorrents(): Promise<TorrentSummary[]> {
  const client = await getClient();
  return client.torrents.map(summarize);
}

export async function getTorrent(infoHash: string): Promise<TorrentSummary | null> {
  const client = await getClient();
  const t = client.torrents.find((t2: WTTorrent) => t2.infoHash === infoHash);
  return t ? summarize(t) : null;
}

export async function pauseTorrent(infoHash: string): Promise<boolean> {
  const client = await getClient();
  const t = client.torrents.find((t2: WTTorrent) => t2.infoHash === infoHash);
  if (!t) return false;
  t.pause();
  const m = metaMap().get(infoHash);
  if (m) m.userPaused = true;
  return true;
}

export async function resumeTorrent(infoHash: string): Promise<boolean> {
  const client = await getClient();
  const t = client.torrents.find((t2: WTTorrent) => t2.infoHash === infoHash);
  if (!t) return false;
  t.resume();
  const m = metaMap().get(infoHash);
  if (m) m.userPaused = false;
  return true;
}

/** t.pause() n'arrête pas les pairs en WebTorrent v2 — seul remove() le fait réellement (comportement documenté, confirmé côté Movviz). */
export async function removeTorrent(infoHash: string, deleteData: boolean): Promise<boolean> {
  const client = await getClient();
  const t = client.torrents.find((t2: WTTorrent) => t2.infoHash === infoHash);
  if (!t) return false;
  await new Promise<void>((resolve) => client.remove(infoHash, { destroyStore: deleteData }, () => resolve()));
  metaMap().delete(infoHash);
  return true;
}
