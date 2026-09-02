import { innertubePlayer } from "./innertubeClient";
import { resolveStreamUrl } from "./ytDlpResolver";
import type { PlaybackResolution } from "@/lib/contracts/music";

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
  const innertubeResult = await tryInnertube(videoId);
  if (innertubeResult) return innertubeResult;

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
 * `null` pour signaler "tenter le repli yt-dlp" — cas actuel systématique
 * (`signatureCipher` sans `url`, voir commentaire ci-dessus).
 */
async function tryInnertube(videoId: string): Promise<PlaybackResolution | null> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const raw: any = await innertubePlayer(videoId);
  const status = raw?.playabilityStatus?.status;

  if (status === "OK" && raw?.streamingData) {
    const audioFormats = (raw.streamingData.adaptiveFormats ?? []).filter((f: { mimeType?: string }) =>
      f.mimeType?.startsWith("audio")
    );
    const best = audioFormats.sort(
      (a: { bitrate?: number }, b: { bitrate?: number }) => (b.bitrate ?? 0) - (a.bitrate ?? 0)
    )[0];
    if (best?.url) {
      return {
        ok: true,
        source: { type: "PROVIDER", url: best.url, codec: best.mimeType, bitrate: best.bitrate },
      };
    }
  }
  return null;
}
