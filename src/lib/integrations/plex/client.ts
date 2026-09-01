import { safePlexUrl } from "./safeUrl";
import type { PlexConfig } from "./store";

/**
 * Client Plex minimal (plan §53) — jamais un identifiant MUZZIK canonique
 * dérivé d'un ratingKey (INTERDIT 2). Utilisé uniquement pour découvrir/
 * mapper une bibliothèque Plex existante, jamais comme dépendance de lecture.
 */
export async function testPlexConnection(config: Pick<PlexConfig, "serverUrl" | "token">): Promise<{ ok: boolean; detail: string }> {
  const origin = safePlexUrl(config.serverUrl);
  if (!origin) return { ok: false, detail: "URL de serveur invalide ou non autorisée" };
  if (!config.token) return { ok: false, detail: "Token Plex requis" };

  try {
    const res = await fetch(`${origin}/identity`, {
      headers: { "X-Plex-Token": config.token, Accept: "application/json" },
      signal: AbortSignal.timeout(8000),
    });
    if (!res.ok) return { ok: false, detail: `HTTP ${res.status}` };
    const body = await res.json();
    const version = body?.MediaContainer?.version;
    return { ok: true, detail: version ? `Connecté — Plex ${version}` : "Connecté" };
  } catch (e) {
    return { ok: false, detail: e instanceof Error && e.name === "AbortError" ? "timeout" : "serveur injoignable" };
  }
}
