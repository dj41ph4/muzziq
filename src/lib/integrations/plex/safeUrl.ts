/**
 * Validation SSRF (plan §58, porté depuis Movviz src/lib/plex/safeUrl.ts —
 * même logique, générique). Bloque loopback/link-local uniquement — un
 * serveur Plex est typiquement sur une IP LAN privée (192.168.x.x/10.x.x.x),
 * parfaitement valide, jamais bloquée ici.
 */
export function safePlexUrl(hostname: string): string | null {
  if (!hostname) return null;
  try {
    const u = new URL(hostname.includes("://") ? hostname : `http://${hostname}`);
    if (u.protocol !== "http:" && u.protocol !== "https:") return null;
    const host = u.hostname.toLowerCase();
    if (host === "localhost" || host === "0.0.0.0" || host === "::1") return null;
    if (/^127\./.test(host)) return null;
    if (/^169\.254\./.test(host)) return null;
    return u.origin;
  } catch {
    return null;
  }
}
