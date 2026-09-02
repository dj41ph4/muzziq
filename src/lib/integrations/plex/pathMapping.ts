import type { PlexPathMapping } from "./types";

/**
 * Rewrites un chemin tel que rapporté par Plex vers le point de vue MuzziQ
 * (cas réel : le NAS de l'utilisateur monte `/music` côté MuzziQ mais Plex le
 * voit comme `/plex/music`). Comparaison insensible à la casse, tolérante aux
 * deux styles de séparateur. Aucun mapping applicable → chemin inchangé (cas
 * par défaut d'une installation single-container où les deux voient le même
 * chemin).
 */
export function applyPathMapping(plexPath: string, mappings: PlexPathMapping[]): string {
  if (!plexPath || mappings.length === 0) return plexPath;

  const normalized = plexPath.replace(/\\/g, "/");

  let best: PlexPathMapping | null = null;
  for (const m of mappings) {
    if (!m.plexPrefix || !m.localPrefix) continue;
    const prefixNormalized = m.plexPrefix.replace(/\\/g, "/");
    if (normalized.toLowerCase().startsWith(prefixNormalized.toLowerCase())) {
      if (!best || m.plexPrefix.length > best.plexPrefix.length) best = m;
    }
  }
  if (!best) return plexPath;

  const prefixNormalized = best.plexPrefix.replace(/\\/g, "/");
  const rest = normalized.slice(prefixNormalized.length);
  const localSep = best.localPrefix.includes("\\") ? "\\" : "/";
  return best.localPrefix.replace(/[\\/]+$/, "") + rest.split("/").join(localSep);
}
