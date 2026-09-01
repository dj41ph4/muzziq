import { innertubePlayer } from "./innertubeClient";
import type { PlaybackResolution } from "@/lib/contracts/music";

/**
 * Résolution de flux réelle — PAS encore fonctionnelle en mode anonyme.
 *
 * Sondé en conditions réelles le 2026-09-01 sur 6 contextes client
 * (WEB_REMIX, ANDROID_MUSIC, IOS_MUSIC, WEB, MWEB, TVHTML5_SIMPLY_EMBEDDED) :
 * tous renvoient LOGIN_REQUIRED ou UNPLAYABLE sans PoToken (jeton anti-bot
 * généré par un challenge BotGuard). Ce n'est pas une régression du code —
 * c'est l'état réel de la protection YouTube au moment du sondage (même
 * limite documentée par les projets yt-dlp/ytmusicapi).
 *
 * Règle absolue du plan d'architecture (§79, INTERDIT) : ne jamais fabriquer
 * un flux de lecture qui ne marche pas. Cette fonction renvoie un échec
 * explicite et typé plutôt qu'une URL invalide — c'est à ProviderHealth (§16)
 * de rendre ce statut visible, et au Playback Resolver MUZZIK (§12, pas
 * encore construit) de retomber sur une autre source le cas échéant.
 *
 * Prochaine étape pour lever cette limite : implémenter un PoTokenManager
 * (§13 du plan) — génère un PoToken via un solveur BotGuard (VM JS headless,
 * pattern documenté par bgutil-ytdlp-pot-provider). C'est un sous-projet à
 * part entière, pas une correction ponctuelle.
 */
export async function resolveYoutubeMusicPlayback(videoId: string): Promise<PlaybackResolution> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const raw: any = await innertubePlayer(videoId);
  const status = raw?.playabilityStatus?.status;
  const reason = raw?.playabilityStatus?.reason ?? "Raison inconnue";

  if (status === "OK" && raw?.streamingData) {
    // Chemin non encore atteint en conditions réelles (voir commentaire
    // ci-dessus) — laissé en place pour le jour où un PoTokenManager
    // débloque ce statut, plutôt que de deviner la forme exacte maintenant.
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

  if (status === "LOGIN_REQUIRED") {
    return { ok: false, status: "AUTH_REQUIRED", reason: `${reason} — PoToken requis (non implémenté)` };
  }
  return { ok: false, status: "BROKEN", reason: `playabilityStatus=${status ?? "absent"} (${reason})` };
}
