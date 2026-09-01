import type { ExternalTrack } from "@/lib/contracts/music";
import type { MediaFile } from "@/lib/library/mediaFilesStore";

/**
 * IdentityResolver minimal (plan §7, INTERDIT 11). Matching par score de
 * confiance explicite — jamais un simple booléen. Une identité reste
 * UNRESOLVED plutôt que fusionnée à l'aveugle sous le seuil : "Numb" et
 * "Numb - 2003 Remaster" ne doivent pas se confondre juste parce que le
 * titre et la durée sont proches.
 *
 * Version V1 : normalisation texte + tolérance de durée. Pas encore de
 * MusicBrainz ID / ISRC / fingerprint (plan §7 points 1-2, 8) — ces signaux
 * n'existent pas encore dans ce bootstrap, viendront avec les providers
 * MusicBrainz et le fingerprinting (plan §31), pas avant qu'un besoin réel
 * de désambiguïsation le justifie.
 */

export const RESOLUTION_CONFIDENCE_THRESHOLD = 0.75;
const DURATION_TOLERANCE_SECONDS = 4;

function normalize(text: string): string {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "") // accents
    .replace(/\([^)]*\)|\[[^\]]*\]/g, "") // "(Remaster)", "[Explicit]"...
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export interface IdentityMatch {
  mediaFile: MediaFile;
  confidence: number;
}

/** Score de confiance entre un résultat externe et un fichier local — jamais un booléen. */
export function matchConfidence(external: ExternalTrack, local: MediaFile): number {
  const artistMatch = normalize(external.artist) === normalize(local.artist) ? 0.4 : 0;

  const nTitleExt = normalize(external.title);
  const nTitleLocal = normalize(local.title);
  const titleMatch = nTitleExt === nTitleLocal ? 0.4 : nTitleExt.includes(nTitleLocal) || nTitleLocal.includes(nTitleExt) ? 0.2 : 0;

  let durationMatch = 0;
  if (external.durationSeconds && local.durationSeconds) {
    const delta = Math.abs(external.durationSeconds - local.durationSeconds);
    durationMatch = delta <= DURATION_TOLERANCE_SECONDS ? 0.2 : 0;
  }

  return artistMatch + titleMatch + durationMatch;
}

/** Meilleure correspondance locale pour un résultat externe, ou undefined si rien n'atteint le seuil de confiance (reste UNRESOLVED). */
export function resolveLocalMatch(external: ExternalTrack, candidates: MediaFile[]): IdentityMatch | undefined {
  let best: IdentityMatch | undefined;
  for (const local of candidates) {
    const confidence = matchConfidence(external, local);
    if (confidence >= RESOLUTION_CONFIDENCE_THRESHOLD && (!best || confidence > best.confidence)) {
      best = { mediaFile: local, confidence };
    }
  }
  return best;
}
