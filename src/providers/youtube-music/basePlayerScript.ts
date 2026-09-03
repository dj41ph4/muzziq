/**
 * Cache partagé du script `base.js` du lecteur YouTube (music.youtube.com).
 *
 * Extrait de `signatureTimestamp.ts` le 2026-09-03 pour que
 * `signatureCipher.ts` (déchiffrement de `signatureCipher`, voir ce fichier
 * et `docs/reverse-engineering/youtube-music/README.md`) puisse réutiliser
 * le même script déjà téléchargé plutôt que de refaire les deux requêtes
 * HTTP (page d'accueil + `base.js`) à chaque appel. Aucune exécution de code
 * n'a lieu ici — ce module ne fait que récupérer et mettre en cache le texte
 * brut du script, exactement comme avant l'extraction.
 */

const g = globalThis as typeof globalThis & {
  __muzziqYtBaseJs?: { text: string; path: string; fetchedAt: number } | null;
  __muzziqYtBaseJsPromise?: Promise<{ text: string; path: string } | null> | null;
};

const BASE_JS_TTL_MS = 6 * 60 * 60 * 1000; // 6h — même politique que le sts, un déploiement du lecteur est rare.
export const BROWSER_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

async function fetchCurrentBaseJs(): Promise<{ text: string; path: string } | null> {
  try {
    const homeRes = await fetch("https://music.youtube.com/", {
      headers: { "User-Agent": BROWSER_USER_AGENT },
      signal: AbortSignal.timeout(8000),
    });
    if (!homeRes.ok) return null;
    const html = await homeRes.text();
    const playerPathMatch = html.match(/\/s\/player\/[a-zA-Z0-9_/.]+base\.js/);
    if (!playerPathMatch) return null;

    const playerRes = await fetch(`https://music.youtube.com${playerPathMatch[0]}`, {
      headers: { "User-Agent": BROWSER_USER_AGENT },
      signal: AbortSignal.timeout(8000),
    });
    if (!playerRes.ok) return null;
    const text = await playerRes.text();
    return { text, path: playerPathMatch[0] };
  } catch {
    return null;
  }
}

/** Renvoie le `base.js` courant (texte brut, caché ~6h), ou `null` si indisponible (jamais bloquant). */
export async function getBasePlayerScript(): Promise<{ text: string; path: string } | null> {
  const cached = g.__muzziqYtBaseJs;
  if (cached && Date.now() - cached.fetchedAt < BASE_JS_TTL_MS) {
    return { text: cached.text, path: cached.path };
  }

  if (!g.__muzziqYtBaseJsPromise) {
    g.__muzziqYtBaseJsPromise = fetchCurrentBaseJs().then((result) => {
      g.__muzziqYtBaseJsPromise = null;
      if (result !== null) {
        g.__muzziqYtBaseJs = { ...result, fetchedAt: Date.now() };
      }
      return result;
    });
  }
  const fresh = await g.__muzziqYtBaseJsPromise;
  if (fresh !== null) return fresh;
  return cached ? { text: cached.text, path: cached.path } : null;
}
