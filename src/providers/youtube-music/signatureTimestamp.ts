/**
 * Cache du `signatureTimestamp` ("sts") InnerTube.
 *
 * Découverte réelle du 2026-09-02 (voir docs/reverse-engineering/youtube-music) :
 * la conclusion du 2026-09-01 ("PoToken/BotGuard obligatoire, tous contextes
 * bloqués") était incomplète. En reprenant l'investigation en s'inspirant de la
 * stratégie de clients tiers YouTube Music open source, un appel `/player`
 * anonyme (WEB_REMIX, aucun cookie, aucun visitorData, aucun PoToken) passe de
 * `UNPLAYABLE` à `OK` + `streamingData` dès qu'on ajoute
 * `playbackContext.contentPlaybackContext.signatureTimestamp` — un entier
 * simple, pas un jeton BotGuard. Vérifié par appel réel contre plusieurs
 * videoId distincts, avec zéro cookie et zéro visitorData.
 *
 * Ce `sts` change à chaque déploiement du lecteur YouTube (`base.js`) — il y
 * est embarqué en clair (`signatureTimestamp:20684`). On le récupère en
 * extrayant la constante du script courant (voir `basePlayerScript.ts` pour
 * le téléchargement/cache du script lui-même, partagé avec
 * `signatureCipher.ts`) par une simple expression régulière — aucune
 * exécution de JavaScript distant, aucun bac à sable, aucun navigateur.
 */

import { getBasePlayerScript } from "./basePlayerScript";

const g = globalThis as typeof globalThis & {
  __muzziqYtSts?: { value: number; fetchedAt: number } | null;
  __muzziqYtStsPromise?: Promise<number | null> | null;
};

const STS_TTL_MS = 6 * 60 * 60 * 1000; // 6h — le sts ne change qu'à un déploiement du lecteur, marge large.

async function fetchCurrentSts(): Promise<number | null> {
  const script = await getBasePlayerScript();
  if (!script) return null;
  const stsMatch = script.text.match(/signatureTimestamp:(\d+)/);
  if (!stsMatch) return null;
  return Number(stsMatch[1]);
}

/** Renvoie le `signatureTimestamp` courant (caché ~6h), ou `null` si indisponible (jamais bloquant). */
export async function getSignatureTimestamp(): Promise<number | null> {
  const cached = g.__muzziqYtSts;
  if (cached && Date.now() - cached.fetchedAt < STS_TTL_MS) {
    return cached.value;
  }

  // Un seul fetch en vol à la fois même si plusieurs requêtes arrivent en
  // même temps (évite N appels concurrents vers music.youtube.com au démarrage).
  if (!g.__muzziqYtStsPromise) {
    g.__muzziqYtStsPromise = fetchCurrentSts().then((value) => {
      g.__muzziqYtStsPromise = null;
      if (value !== null) {
        g.__muzziqYtSts = { value, fetchedAt: Date.now() };
      }
      return value;
    });
  }
  const fresh = await g.__muzziqYtStsPromise;
  return fresh ?? cached?.value ?? null;
}
