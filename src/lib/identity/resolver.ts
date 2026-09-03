import type { ExternalTrack } from "@/lib/contracts/music";
import type { MediaFile } from "@/lib/library/mediaFilesStore";

/**
 * IdentityResolver minimal (plan §7, INTERDIT 11). Matching par score de
 * confiance explicite — jamais un simple booléen. Une identité reste
 * UNRESOLVED plutôt que fusionnée à l'aveugle sous le seuil : "Numb" et
 * "Numb - 2003 Remaster" ne doivent pas se confondre juste parce que le
 * titre et la durée sont proches.
 *
 * Version V2 : normalisation texte + tolérance de durée + signal ISRC quand
 * disponible (plan §48/§104 — provider mapping connu > ISRC exact > le
 * reste). Pas encore de MusicBrainz ID / fingerprint (plan §7 points 1-2, 8)
 * — ces signaux n'existent pas encore dans ce bootstrap.
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

/**
 * Marqueurs qui changent l'enregistrement lui-même (pas juste une variante
 * de mastering/édition) — jamais anodins à ignorer. `normalize()` retire
 * TOUT contenu entre parenthèses/crochets, donc "Numb (Live)" et "Numb"
 * deviennent identiques après normalisation ; sans ce garde-fou, un score
 * textuel élevé (artiste+titre+durée proche) peut dépasser le seuil de
 * fusion pour deux enregistrements réellement différents (plan §104 —
 * cas de test explicite "live"). Recherché dans le titre BRUT (pas normalisé)
 * pour ne pas dépendre de ce que `normalize()` a déjà supprimé.
 */
const DISTINGUISHING_MARKERS = ["live", "remix", "acoustic", "instrumental", "a cappella", "karaoke", "cover", "demo"];

function distinguishingMarkers(title: string): Set<string> {
  const lower = title.toLowerCase();
  return new Set(DISTINGUISHING_MARKERS.filter((marker) => lower.includes(marker)));
}

/** Vrai si un titre porte un marqueur d'enregistrement distinct que l'autre n'a pas (ex. "Numb (Live)" vs "Numb"). */
function hasAsymmetricMarker(a: string, b: string): boolean {
  const markersA = distinguishingMarkers(a);
  const markersB = distinguishingMarkers(b);
  for (const marker of markersA) if (!markersB.has(marker)) return true;
  for (const marker of markersB) if (!markersA.has(marker)) return true;
  return false;
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
  isrc?: string;
}

function normalizeIsrc(isrc: string): string {
  return isrc.toUpperCase().replace(/[^A-Z0-9]/g, "");
}

/** Score de confiance entre deux morceaux (titre/artiste/durée/ISRC) — jamais un booléen. Cœur partagé par matchConfidence et tout autre appariement (import Plex, etc.). */
export function trackMatchConfidence(a: MatchableTrack, b: MatchableTrack): number {
  if (a.isrc && b.isrc) {
    const isrcA = normalizeIsrc(a.isrc);
    const isrcB = normalizeIsrc(b.isrc);
    if (isrcA && isrcB) {
      // ISRC identique = certitude (plan §48 : "ISRC exact +1.00 → certitude").
      if (isrcA === isrcB) return 1;
      // ISRC différent malgré titre/artiste/durée proches = versions distinctes
      // (remaster, live, édition...) — signal négatif fort, jamais ignoré au
      // profit des autres heuristiques (INTERDIT 7 : jamais de fusion sous
      // confiance, y compris une fusion qu'un score textuel aurait autorisée).
      return 0;
    }
  }

  // Un marqueur d'enregistrement asymétrique (live/remix/acoustique...) est
  // un signal négatif fort qui prime sur un score textuel/durée par ailleurs
  // élevé — jamais de fusion entre deux versions réellement différentes.
  if (hasAsymmetricMarker(a.title, b.title)) return 0;

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
