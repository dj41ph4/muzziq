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

/** Champs minimaux nécessaires pour comparer deux morceaux, indépendamment de leur type concret (MediaFile, Recording, piste Plex...). */
export interface MatchableTrack {
  title: string;
  artist: string;
  durationSeconds?: number;
}

/** Score de confiance entre deux morceaux (titre/artiste/durée) — jamais un booléen. Cœur partagé par matchConfidence et tout autre appariement (import Plex, etc.). */
export function trackMatchConfidence(a: MatchableTrack, b: MatchableTrack): number {
  const artistMatch = normalize(a.artist) === normalize(b.artist) ? 0.4 : 0;

  const nTitleA = normalize(a.title);
  const nTitleB = normalize(b.title);
  const titleMatch = nTitleA === nTitleB ? 0.4 : nTitleA.includes(nTitleB) || nTitleB.includes(nTitleA) ? 0.2 : 0;

  let durationMatch = 0;
  if (a.durationSeconds && b.durationSeconds) {
    const delta = Math.abs(a.durationSeconds - b.durationSeconds);
    durationMatch = delta <= DURATION_TOLERANCE_SECONDS ? 0.2 : 0;
  }

  return artistMatch + titleMatch + durationMatch;
}

/** Score de confiance entre un résultat externe et un fichier local — jamais un booléen. */
export function matchConfidence(external: ExternalTrack, local: MediaFile): number {
  return trackMatchConfidence(external, local);
}

/** Meilleure correspondance parmi une liste de candidats "matchables" (titre/artiste/durée), ou undefined sous le seuil (INTERDIT 7 : jamais de fusion sous confiance). */
export function bestMatch<T extends MatchableTrack>(external: MatchableTrack, candidates: T[]): { candidate: T; confidence: number } | undefined {
  let best: { candidate: T; confidence: number } | undefined;
  for (const candidate of candidates) {
    const confidence = trackMatchConfidence(external, candidate);
    if (confidence >= RESOLUTION_CONFIDENCE_THRESHOLD && (!best || confidence > best.confidence)) {
      best = { candidate, confidence };
    }
  }
  return best;
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
