import { innertubeSearch, innertubePlayer, InnertubeError } from "./innertubeClient";
import type { ProviderHealthReport, ProviderProbeStatus } from "@/lib/contracts/music";

/**
 * Sondes de santé réelles (plan §16) — chaque probe fait un vrai appel réseau,
 * jamais une supposition sur l'état du provider. C'est ce qui a manqué à
 * Movviz pour la détection de capacités matérielles (plan §105.6) : ne jamais
 * se fier à une capacité déclarée sans la tester en exécution réelle.
 */

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function probeSearch(): Promise<{ status: ProviderProbeStatus; detail?: string }> {
  try {
    const raw: any = await innertubeSearch("test", undefined);
    const hasContent = !!raw?.contents;
    return hasContent ? { status: "OK" } : { status: "DEGRADED", detail: "Réponse sans contenu exploitable" };
  } catch (err) {
    if (err instanceof InnertubeError) {
      return { status: err.httpStatus === 429 ? "RATE_LIMITED" : "BROKEN", detail: err.message };
    }
    return { status: "BROKEN", detail: String(err) };
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function probePlayer(): Promise<{ status: ProviderProbeStatus; detail?: string }> {
  try {
    // Vidéo de test volontairement stable et connue publique.
    const raw: any = await innertubePlayer("jNQXAC9IVRw");
    const status = raw?.playabilityStatus?.status;
    if (status === "OK" && raw?.streamingData) return { status: "OK" };
    if (status === "LOGIN_REQUIRED") {
      return { status: "AUTH_REQUIRED", detail: "PoToken requis en mode anonyme (voir playbackResolver.ts)" };
    }
    return { status: "DEGRADED", detail: `playabilityStatus=${status ?? "absent"}` };
  } catch (err) {
    if (err instanceof InnertubeError) {
      return { status: err.httpStatus === 429 ? "RATE_LIMITED" : "BROKEN", detail: err.message };
    }
    return { status: "BROKEN", detail: String(err) };
  }
}

export async function checkYoutubeMusicHealth(): Promise<ProviderHealthReport> {
  const [search, player] = await Promise.all([probeSearch(), probePlayer()]);
  return {
    provider: "youtube-music",
    checkedAt: new Date().toISOString(),
    probes: { search, player },
  };
}
