import type { ParsedRelease } from "./releaseParser";
import type { QualityProfile } from "./qualityProfiles";

/**
 * Scoring musique (plan §25) — valeurs de départ tirées du plan, configurables
 * plus tard si un besoin réel de réglage apparaît. Une pénalité -1000 doit
 * rendre un candidat pratiquement inéligible sans avoir à filtrer ailleurs.
 */

export const SCORE_WEIGHTS = {
  exactArtist: 40,
  exactAlbum: 50,
  correctYear: 15,
  preferredEdition: 25,
  flac: 30,
  bit24: 10,
  preferredSampleRate: 5,
  correctTrackCount: 25,
  goodSeedCount: 10,
  freshRelease: 5,
  wrongAlbum: -1000,
  wrongArtist: -1000,
  incompleteTracklist: -100,
  suspiciousTranscode: -80,
  wrongEdition: -20,
} as const;

export interface ScoringTarget {
  artist: string;
  album: string;
  year?: number;
  expectedTrackCount?: number;
  preferredEdition?: string;
}

export interface ReleaseCandidateForScoring {
  parsed: ParsedRelease;
  seeders: number;
  publishedAt?: Date;
}

export interface ScoreResult {
  score: number;
  reasons: string[];
}

function normalize(text: string): string {
  return text.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[^a-z0-9\s]/g, " ").replace(/\s+/g, " ").trim();
}

/**
 * Une transcodage suspect (lossy réencodé en FLAC — le fichier prétend être
 * lossless mais un bitrate explicite dans le nom trahit une source lossy)
 * est un signal fort à pénaliser plutôt qu'à accepter aveuglément un FLAC.
 */
function looksLikeSuspiciousTranscode(parsed: ParsedRelease): boolean {
  return parsed.lossless && parsed.bitrateKbps !== undefined && parsed.bitrateKbps < 320;
}

export function scoreRelease(
  candidate: ReleaseCandidateForScoring,
  target: ScoringTarget,
  profile: QualityProfile
): ScoreResult {
  const { parsed } = candidate;
  const w = SCORE_WEIGHTS;
  let score = 0;
  const reasons: string[] = [];

  function add(points: number, reason: string) {
    score += points;
    reasons.push(`${points >= 0 ? "+" : ""}${points} ${reason}`);
  }

  const artistMatch = parsed.artist ? normalize(parsed.artist) === normalize(target.artist) : false;
  if (artistMatch) add(w.exactArtist, "artiste exact");
  else if (parsed.artist) add(w.wrongArtist, "artiste différent");

  const albumMatch = parsed.album ? normalize(parsed.album) === normalize(target.album) : false;
  if (albumMatch) add(w.exactAlbum, "album exact");
  else if (parsed.album) add(w.wrongAlbum, "album différent");

  if (target.year && parsed.year === target.year) add(w.correctYear, "année correcte");

  if (target.preferredEdition) {
    if (parsed.edition && normalize(parsed.edition) === normalize(target.preferredEdition)) {
      add(w.preferredEdition, "édition préférée");
    } else if (parsed.edition) {
      add(w.wrongEdition, "édition différente de celle demandée");
    }
  }

  if (parsed.codec === "FLAC") add(w.flac, "FLAC");
  if (parsed.bitDepth === 24) add(w.bit24, "24-bit");
  if (profile.cutoff.minSampleRateKHz && parsed.sampleRateKHz && parsed.sampleRateKHz >= profile.cutoff.minSampleRateKHz) {
    add(w.preferredSampleRate, "sample rate conforme au profil");
  }

  if (target.expectedTrackCount && parsed.trackCount) {
    if (parsed.trackCount === target.expectedTrackCount) add(w.correctTrackCount, "nombre de pistes correct");
    else if (parsed.trackCount < target.expectedTrackCount) add(w.incompleteTracklist, "tracklist probablement incomplète");
  }

  if (looksLikeSuspiciousTranscode(parsed)) add(w.suspiciousTranscode, "transcodage suspect (FLAC issu d'un bitrate lossy)");

  if (candidate.seeders >= 5) add(w.goodSeedCount, "bon nombre de seeders");

  if (candidate.publishedAt) {
    const ageDays = (Date.now() - candidate.publishedAt.getTime()) / 86_400_000;
    if (ageDays <= 30) add(w.freshRelease, "release récente");
  }

  return { score, reasons };
}
