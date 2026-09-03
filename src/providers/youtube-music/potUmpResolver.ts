/**
 * Orchestration du chemin PoToken + UMP (voir `poTokenBrowser.ts` et
 * `umpParser.ts`) : capture une URL de lecture audio réelle, la rejoue avec
 * `range=0-{clen-1}` (le `range` n'est PAS couvert par `sig`/`lsig`, voir
 * `sparams`/`lsparams` dans la vraie réponse — modifier ce paramètre ne
 * casse pas la signature, vérifié réellement), et parse la réponse UMP.
 *
 * `complete: false` (piste trop longue pour tenir dans une seule part MEDIA,
 * voir `umpParser.ts`) fait échouer cette résolution — jamais de flux
 * tronqué renvoyé comme si c'était le flux complet. L'appelant
 * (`playbackResolver.ts`) se rabat alors sur `ytDlpResolver.ts`.
 */

import { captureAudioPlaybackUrl } from "./poTokenBrowser";
import { parseUmpMedia } from "./umpParser";

export interface PotUmpResult {
  audioBytes: Buffer;
  contentType: string;
}

interface CacheEntry {
  buf: Buffer;
  contentType: string;
  cachedAt: number;
}

const g = globalThis as typeof globalThis & {
  __muzziqPotUmpCache?: Map<string, CacheEntry>;
};

// Les octets extraits sont mis en cache brièvement (le temps de servir la
// requête de streaming qui suit immédiatement la résolution) — pas un cache
// de bibliothèque, juste éviter de refaire tout le pipeline (navigateur +
// fetch + parse) entre la résolution et le premier octet servi au lecteur.
const CACHE_TTL_MS = 10 * 60 * 1000;

function getCache(): Map<string, CacheEntry> {
  if (!g.__muzziqPotUmpCache) g.__muzziqPotUmpCache = new Map();
  return g.__muzziqPotUmpCache;
}

export function getCachedPotUmpAudio(videoId: string): PotUmpResult | null {
  const cached = getCache().get(videoId);
  if (!cached || Date.now() - cached.cachedAt >= CACHE_TTL_MS) return null;
  return { audioBytes: cached.buf, contentType: cached.contentType };
}

/**
 * Tente de résoudre l'audio complet de `videoId` via PoToken + UMP. Renvoie
 * `null` (jamais d'exception) si le navigateur headless n'est pas
 * disponible, si aucune requête média n'a pu être capturée, ou si le flux
 * est trop long pour tenir dans une seule part MEDIA (voir `umpParser.ts`).
 */
export async function resolveViaPotUmp(videoId: string): Promise<PotUmpResult | null> {
  const cached = getCachedPotUmpAudio(videoId);
  if (cached) return cached;

  const capturedUrl = await captureAudioPlaybackUrl(videoId).catch(() => null);
  if (!capturedUrl) return null;

  const url = new URL(capturedUrl);
  const clen = Number(url.searchParams.get("clen"));
  if (!clen || Number.isNaN(clen) || clen <= 0) return null;
  url.searchParams.set("range", `0-${clen - 1}`);

  let res: Response;
  try {
    res = await fetch(url.toString(), { signal: AbortSignal.timeout(20000) });
  } catch {
    return null;
  }
  if (!res.ok) return null;

  const buf = Buffer.from(await res.arrayBuffer());
  const { mediaBytes, complete } = parseUmpMedia(buf, clen);
  if (!complete) return null;

  const contentType = url.searchParams.get("mime") ?? "audio/webm";
  getCache().set(videoId, { buf: mediaBytes, contentType, cachedAt: Date.now() });
  return { audioBytes: mediaBytes, contentType };
}
