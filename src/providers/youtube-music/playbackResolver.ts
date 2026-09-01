import { innertubePlayer } from "./innertubeClient";
import { resolveStreamUrl } from "./ytDlpResolver";
import type { PlaybackResolution } from "@/lib/contracts/music";

/**
 * Résolution de flux (plan §12/§13).
 *
 * InnerTube en anonyme (WEB_REMIX et 5 autres contextes testés le
 * 2026-09-01, voir docs/reverse-engineering/youtube-music) est bloqué sans
 * PoToken. Repli sur yt-dlp en subprocess (§105/réflexion d'architecture —
 * réutilisation d'une librairie mature plutôt que réimplémenter un
 * solveur BotGuard, §87.4). Le chemin InnerTube reste tenté en premier et
 * n'est jamais supprimé : si YouTube change de comportement demain (ou
 * qu'un PoTokenManager maison est construit plus tard), ce chemin redevient
 * actif sans rien retoucher côté appelant.
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

/** Renvoie une résolution si InnerTube a réussi, ou `null` pour signaler "tenter le repli yt-dlp". */
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
