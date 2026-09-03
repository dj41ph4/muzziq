import { innertubePlayer } from "./innertubeClient";
import { resolveStreamUrl } from "./ytDlpResolver";
import { resolveViaPotUmp } from "./potUmpResolver";
import type { PlaybackResolution } from "@/lib/contracts/music";

/**
 * Plafond observé réellement (voir `umpParser.ts`) : une seule part MEDIA
 * UMP ne contient qu'environ 2 097 105 octets — identique sur deux captures
 * indépendantes de contenus différents. Au-delà, `resolveViaPotUmp` renvoie
 * `null` (flux tronqué détecté, jamais servi comme complet) — mais ce
 * travail (navigateur headless + fetch de plusieurs Mo + parse) a déjà un
 * coût réel. Cette marge (mesurée < plafond réel) sert à éviter de le payer
 * pour les pistes qui, à en juger par `contentLength` déjà connu via
 * InnerTube (sans PoToken), n'ont de toute façon aucune chance de tenir en
 * une seule part.
 */
const POT_UMP_SIZE_GUARD_BYTES = 2_000_000;

/**
 * Résolution de flux (plan §12/§13).
 *
 * Mise à jour réelle du 2026-09-02 (voir docs/reverse-engineering/youtube-music) :
 * la conclusion du 2026-09-01 ("PoToken/BotGuard obligatoire, tous contextes
 * bloqués") était erronée. Avec `playbackContext.contentPlaybackContext.
 * signatureTimestamp` ajouté (voir innertubeClient.ts), `/player` en anonyme
 * répond bien `OK` + `streamingData` — sans PoToken, sans cookie, sans
 * visitorData. Le vrai obstacle restant est ailleurs : chaque format renvoyé
 * est protégé par `signatureCipher` (déchiffrement de signature lié à
 * `base.js`, algorithme qui change à chaque déploiement du lecteur YouTube).
 * Vérifié réel sur un échantillon de morceaux du catalogue YT Music : 100%
 * des formats (audio et vidéo, adaptatifs et progressifs) portent
 * `signatureCipher`, aucun `url` en clair — pas un cas particulier à
 * contourner, une règle systématique observée.
 *
 * Décision délibérée : ne PAS réimplémenter ce déchiffrement ici. C'est une
 * surface de reverse engineering aussi mouvante que BotGuard (code obfusqué
 * qui change à chaque déploiement), et yt-dlp la maintient déjà pour de vrai
 * (§87.4 — ne pas réinventer une librairie mature). `tryInnertube` reste donc
 * tenté en premier (utile si YouTube sert un jour un `url` en clair — ça
 * arrive pour certains formats hérités) mais, dans l'état actuel constaté,
 * retombe systématiquement sur le repli yt-dlp, qui gère le déchiffrement.
 * Le chemin InnerTube n'est jamais supprimé : si YouTube change de
 * comportement, ce chemin redevient actif sans rien retoucher côté appelant.
 */
export async function resolveYoutubeMusicPlayback(videoId: string): Promise<PlaybackResolution> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const raw: any = await innertubePlayer(videoId).catch(() => null);

  const innertubeResult = tryInnertube(raw);
  if (innertubeResult) return innertubeResult;

  const potUmpResult = await tryPotUmp(videoId, raw);
  if (potUmpResult) return potUmpResult;

  try {
    const format = await resolveStreamUrl(videoId);
    return {
      ok: true,
      source: { type: "PROVIDER", url: format.url, codec: format.acodec ?? format.ext, bitrate: format.abr },
    };
  } catch (err) {
    return { ok: false, status: "BROKEN", reason: err instanceof Error ? err.message : String(err) };
  }
}

/**
 * Renvoie une résolution si InnerTube a réussi avec un `url` en clair, ou
 * `null` pour signaler "tenter le chemin suivant" — cas actuel systématique
 * (`signatureCipher` sans `url`, voir commentaire ci-dessus).
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function tryInnertube(raw: any): PlaybackResolution | null {
  const status = raw?.playabilityStatus?.status;

  if (status === "OK" && raw?.streamingData) {
    const best = bestAudioFormat(raw);
    if (best?.url) {
      return {
        ok: true,
        source: { type: "PROVIDER", url: best.url, codec: best.mimeType, bitrate: best.bitrate },
      };
    }
  }
  return null;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function bestAudioFormat(raw: any): { url?: string; mimeType?: string; bitrate?: number; contentLength?: string } | null {
  const audioFormats = (raw?.streamingData?.adaptiveFormats ?? []).filter((f: { mimeType?: string }) =>
    f.mimeType?.startsWith("audio")
  );
  if (audioFormats.length === 0) return null;
  return audioFormats.sort(
    (a: { bitrate?: number }, b: { bitrate?: number }) => (b.bitrate ?? 0) - (a.bitrate ?? 0)
  )[0];
}

/**
 * Chemin PoToken + UMP (voir `potUmpResolver.ts`) : ne produit un flux
 * réellement complet que pour les pistes courtes (voir
 * `POT_UMP_SIZE_GUARD_BYTES` et `umpParser.ts`). `contentLength` est déjà
 * connu via la réponse InnerTube ci-dessus (sans PoToken) — s'il dépasse
 * largement le plafond observé d'une seule part MEDIA, ce chemin est
 * ignoré : lancer le navigateur headless puis tout de même échouer au
 * dernier moment coûterait plusieurs secondes pour rien sur la quasi-totalité
 * des morceaux réels (quelques minutes chacun), qui dépassent presque tous
 * ce plafond.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function tryPotUmp(videoId: string, raw: any): Promise<PlaybackResolution | null> {
  const best = bestAudioFormat(raw);
  const knownLength = best?.contentLength ? Number(best.contentLength) : null;
  if (knownLength !== null && !Number.isNaN(knownLength) && knownLength > POT_UMP_SIZE_GUARD_BYTES) {
    return null;
  }

  try {
    const result = await resolveViaPotUmp(videoId);
    if (!result) return null;
    return {
      ok: true,
      source: { type: "PROVIDER", url: `/api/youtube-music/stream/${videoId}`, codec: result.contentType },
    };
  } catch {
    return null;
  }
}
