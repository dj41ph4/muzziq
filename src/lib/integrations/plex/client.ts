import { safePlexUrl } from "./safeUrl";
import type { PlexConfig } from "./store";
import type { PlexAccount, PlexRawTrack, PlexSection, PlexServerOption } from "./types";

/**
 * Client Plex (plan §53) — jamais un identifiant MuzziQ canonique dérivé d'un
 * ratingKey (INTERDIT 2). Utilisé pour : l'authentification OAuth PIN, la
 * découverte de serveurs/sections, la synchronisation de bibliothèque et
 * d'historique d'écoute. Jamais utilisé comme dépendance de lecture (§10) —
 * le Playback Resolver reste local.
 *
 * Endpoints/flow OAuth PIN portés depuis Movviz (src/lib/plex/client.ts) —
 * mêmes appels plex.tv, adaptés au domaine musique (pas de watchlist, pas de
 * markers, historique = écoute plutôt que visionnage).
 */

const PRODUCT = "MuzziQ";

function accountHeaders(clientId: string, extra?: Record<string, string>) {
  return {
    accept: "application/json",
    "x-plex-product": PRODUCT,
    "x-plex-client-identifier": clientId,
    ...extra,
  };
}

/** plain fetch() ne timeout jamais tout seul — un serveur qui traîne sur une requête bloquerait un sync entier. */
async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs = 15000): Promise<Response> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: ctrl.signal });
  } finally {
    clearTimeout(timer);
  }
}

/** Retry léger sur erreur réseau transitoire (connexion réutilisée fermée côté serveur, etc.), jamais sur une erreur HTTP. */
async function fetchWithRetry(url: string, init: RequestInit, timeoutMs = 15000, retries = 2): Promise<Response> {
  for (let attempt = 0; ; attempt++) {
    try {
      return await fetchWithTimeout(url, init, timeoutMs);
    } catch (e) {
      if (attempt >= retries) throw e;
      await new Promise((r) => setTimeout(r, 400 * (attempt + 1)));
    }
  }
}

// ─── OAuth PIN flow (plex.tv) ───────────────────────────────────────────────

export async function createPin(clientId: string): Promise<{ id: number; code: string } | null> {
  try {
    const res = await fetch("https://plex.tv/api/v2/pins?strong=true", {
      method: "POST",
      headers: accountHeaders(clientId),
    });
    if (!res.ok) return null;
    const data = await res.json();
    return { id: data.id, code: data.code };
  } catch {
    return null;
  }
}

export function buildAuthUrl(clientId: string, code: string, forwardUrl?: string): string {
  const params = new URLSearchParams({
    clientID: clientId,
    code,
    "context[device][product]": PRODUCT,
  });
  if (forwardUrl) params.set("forwardUrl", forwardUrl);
  return `https://app.plex.tv/auth#?${params.toString()}`;
}

/** À interroger après avoir envoyé l'utilisateur vers buildAuthUrl() — renvoie le token de compte une fois autorisé, sinon null (pas encore autorisé/expiré). */
export async function checkPin(clientId: string, pinId: number): Promise<string | null> {
  try {
    const res = await fetch(`https://plex.tv/api/v2/pins/${pinId}`, {
      headers: accountHeaders(clientId),
      cache: "no-store",
    });
    if (!res.ok) return null;
    const data = await res.json();
    return data.authToken || null;
  } catch {
    return null;
  }
}

export async function getPlexAccount(clientId: string, token: string): Promise<PlexAccount | null> {
  try {
    const res = await fetch("https://plex.tv/api/v2/user", {
      headers: accountHeaders(clientId, { "x-plex-token": token }),
      cache: "no-store",
    });
    if (!res.ok) return null;
    const d = await res.json();
    return {
      id: String(d.id),
      uuid: d.uuid,
      username: d.username || d.title || d.email,
      email: d.email,
      thumb: d.thumb || null,
      authToken: token,
    };
  } catch {
    return null;
  }
}

// ─── Découverte serveurs / sections ─────────────────────────────────────────

interface RawResourceConnection {
  uri?: string;
  local?: boolean;
  relay?: boolean;
}
interface RawResource {
  name?: string;
  clientIdentifier?: string;
  provides?: string;
  owned?: boolean;
  presence?: boolean;
  connections?: RawResourceConnection[];
  Connection?: RawResourceConnection[];
}

/** Serveurs Plex accessibles à ce compte (resources.plex.tv) — remplace la saisie manuelle d'une URL de serveur. */
export async function listPlexServers(clientId: string, accountToken: string): Promise<PlexServerOption[]> {
  try {
    const res = await fetchWithTimeout("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1", {
      headers: accountHeaders(clientId, { "x-plex-token": accountToken }),
      cache: "no-store",
    });
    if (!res.ok) return [];
    const list = (await res.json()) as RawResource[];
    if (!Array.isArray(list)) return [];
    return list
      .filter((r) => (r.provides ?? "").split(",").includes("server"))
      .map((r) => ({
        name: r.name ?? "Serveur Plex",
        machineIdentifier: r.clientIdentifier ?? "",
        owned: !!r.owned,
        connections: (r.connections ?? r.Connection ?? [])
          .filter((c) => !!c.uri)
          .map((c) => ({ uri: c.uri!, local: !!c.local, relay: !!c.relay })),
      }))
      .filter((s) => s.machineIdentifier && s.connections.length > 0);
  } catch {
    return [];
  }
}

function plexOrigin(config: Pick<PlexConfig, "serverUrl">): string | null {
  return safePlexUrl(config.serverUrl);
}

function serverHeaders(clientId: string, token: string) {
  return accountHeaders(clientId, { "x-plex-token": token });
}

export async function testPlexConnection(config: Pick<PlexConfig, "serverUrl" | "token" | "clientId">): Promise<{ ok: boolean; detail: string }> {
  const origin = plexOrigin(config);
  if (!origin) return { ok: false, detail: "URL de serveur invalide ou non autorisée" };
  if (!config.token) return { ok: false, detail: "Token Plex requis" };

  try {
    const res = await fetchWithTimeout(`${origin}/identity`, {
      headers: serverHeaders(config.clientId || "muzziq", config.token),
    }, 8000);
    if (!res.ok) return { ok: false, detail: `HTTP ${res.status}` };
    const body = await res.json();
    const version = body?.MediaContainer?.version;
    return { ok: true, detail: version ? `Connecté — Plex ${version}` : "Connecté" };
  } catch (e) {
    return { ok: false, detail: e instanceof Error && e.name === "AbortError" ? "timeout" : "serveur injoignable" };
  }
}

export async function getServerIdentity(config: Pick<PlexConfig, "serverUrl" | "token" | "clientId">): Promise<string | null> {
  const origin = plexOrigin(config);
  if (!origin) return null;
  try {
    const res = await fetchWithTimeout(`${origin}/identity`, { headers: serverHeaders(config.clientId, config.token) });
    if (!res.ok) return null;
    const data = await res.json();
    return data?.MediaContainer?.machineIdentifier ?? null;
  } catch {
    return null;
  }
}

interface RawDirectory {
  key: string;
  type: string;
  title: string;
}

/** Bibliothèques Plex de type "artist" (musique) sur le serveur configuré — jamais movie/show. */
export async function getMusicSections(config: Pick<PlexConfig, "serverUrl" | "token" | "clientId">): Promise<PlexSection[]> {
  const origin = plexOrigin(config);
  if (!origin || !config.token) return [];
  try {
    const res = await fetchWithRetry(`${origin}/library/sections`, { headers: serverHeaders(config.clientId, config.token) });
    if (!res.ok) return [];
    const data = await res.json();
    const dirs: RawDirectory[] = data?.MediaContainer?.Directory ?? [];
    return dirs.filter((d) => d.type === "artist").map((d) => ({ key: d.key, title: d.title, type: d.type }));
  } catch {
    return [];
  }
}

/**
 * Toutes les pistes d'une section musicale, en une passe paginée
 * (`type=10` = track côté Plex) — pas de marche artiste→album→piste,
 * l'endpoint de section le fait en un seul type de requête.
 */
export async function getSectionTracks(
  config: Pick<PlexConfig, "serverUrl" | "token" | "clientId">,
  sectionKey: string,
  opts?: { sinceUnixSeconds?: number }
): Promise<PlexRawTrack[]> {
  const origin = plexOrigin(config);
  if (!origin || !config.token) return [];
  const out: PlexRawTrack[] = [];
  const pageSize = 200;
  let start = 0;
  const incremental = opts?.sinceUnixSeconds != null;
  for (;;) {
    let page: PlexRawTrack[];
    let total: number;
    try {
      const url = new URL(`${origin}/library/sections/${sectionKey}/all`);
      url.searchParams.set("type", "10");
      if (incremental) url.searchParams.set("sort", "updatedAt:desc");
      const res = await fetchWithRetry(url.toString(), {
        headers: {
          ...serverHeaders(config.clientId, config.token),
          "X-Plex-Container-Start": String(start),
          "X-Plex-Container-Size": String(pageSize),
        },
      });
      if (!res.ok) break;
      const data = await res.json();
      page = data?.MediaContainer?.Metadata ?? [];
      total = data?.MediaContainer?.totalSize ?? page.length;
    } catch {
      break;
    }
    if (incremental) {
      const fresh = page.filter((t) => (t.updatedAt ?? t.addedAt ?? 0) >= opts!.sinceUnixSeconds!);
      out.push(...fresh);
      if (fresh.length < page.length) break; // trié plus récent d'abord : le reste est plus ancien
    } else {
      out.push(...page);
    }
    start += page.length;
    if (page.length === 0 || start >= total) break;
  }
  return out;
}

/** Marque une piste "lue" côté Plex (export, §10 seulement depuis le serveur, jamais depuis l'UI). Best-effort. */
export async function scrobblePlexTrack(config: Pick<PlexConfig, "serverUrl" | "token" | "clientId">, ratingKey: string): Promise<boolean> {
  const origin = plexOrigin(config);
  if (!origin || !config.token) return false;
  try {
    const params = new URLSearchParams({ key: ratingKey, identifier: "com.plexapp.plugins.library" });
    const res = await fetchWithTimeout(`${origin}/:/scrobble?${params}`, { headers: serverHeaders(config.clientId, config.token) });
    return res.ok;
  } catch {
    return false;
  }
}

export interface PlexTrackHistoryEntry {
  ratingKey: string;
  viewedAt: number; // unix seconds
  accountId?: number;
}

interface RawHistoryItem {
  ratingKey: string;
  type?: string;
  viewedAt?: number;
  accountID?: number | string;
}

/**
 * Historique d'écoute (pistes) pour le compte propriétaire du serveur.
 * Comme chez Movviz (getAccountHistory) : `/status/sessions/history/all` est
 * le seul endpoint qui reflète vraiment le compte demandé, jamais
 * `viewCount` sur les endpoints de liste (toujours celui du propriétaire).
 */
export async function getTrackHistory(
  config: Pick<PlexConfig, "serverUrl" | "token" | "clientId">,
  opts?: { sinceUnixSeconds?: number }
): Promise<PlexTrackHistoryEntry[]> {
  const origin = plexOrigin(config);
  if (!origin || !config.token) return [];
  const out: PlexTrackHistoryEntry[] = [];
  const pageSize = 200;
  let start = 0;
  for (;;) {
    let page: RawHistoryItem[];
    let total: number;
    try {
      const url = new URL(`${origin}/status/sessions/history/all`);
      url.searchParams.set("sort", "viewedAt:desc");
      const res = await fetchWithRetry(url.toString(), {
        headers: {
          ...serverHeaders(config.clientId, config.token),
          "X-Plex-Container-Start": String(start),
          "X-Plex-Container-Size": String(pageSize),
        },
      });
      if (!res.ok) break;
      const data = await res.json();
      page = data?.MediaContainer?.Metadata ?? [];
      total = data?.MediaContainer?.totalSize ?? data?.MediaContainer?.size ?? page.length;
    } catch {
      break;
    }
    let hitWatermark = false;
    for (const item of page) {
      if (item.type !== "track") continue;
      if (typeof item.viewedAt !== "number") continue;
      if (opts?.sinceUnixSeconds != null && item.viewedAt <= opts.sinceUnixSeconds) {
        hitWatermark = true;
        break;
      }
      out.push({ ratingKey: item.ratingKey, viewedAt: item.viewedAt, accountId: item.accountID != null ? Number(item.accountID) : undefined });
    }
    start += page.length;
    if (hitWatermark || page.length === 0 || start >= total) break;
  }
  return out;
}
