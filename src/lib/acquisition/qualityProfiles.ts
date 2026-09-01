/**
 * Quality Profiles (plan §26/§27). Constantes pour l'instant — deviendra un
 * store configurable si un vrai besoin de personnalisation par utilisateur
 * apparaît (ne pas préconstruire avant, même logique que §105.3).
 */

export type QualityProfileName = "standard" | "lossless" | "hires";

export interface QualityProfile {
  name: QualityProfileName;
  /** Le morceau/album cesse d'être recherché en upgrade une fois ce niveau atteint. */
  cutoff: { lossless: boolean; minBitDepth?: number; minSampleRateKHz?: number; minBitrateKbps?: number };
}

export const QUALITY_PROFILES: Record<QualityProfileName, QualityProfile> = {
  standard: { name: "standard", cutoff: { lossless: false, minBitrateKbps: 256 } },
  lossless: { name: "lossless", cutoff: { lossless: true, minBitDepth: 16, minSampleRateKHz: 44.1 } },
  hires: { name: "hires", cutoff: { lossless: true, minBitDepth: 24 } },
};

// Un FLAC issu d'un CD n'a quasiment jamais son bit depth/sample rate écrit
// dans le nom — seule une release non-standard (Hi-Res) le précise
// explicitement. Absence d'info ≠ qualité inférieure : le standard CD
// (16-bit/44.1kHz) est l'hypothèse par défaut pour un lossless sans
// précision, jamais 0. Bug réel trouvé par golden test (§93) : "Album
// (2020) FLAC" échouait le cutoff Lossless faute de "44.1kHz" explicite.
const IMPLICIT_CD_BIT_DEPTH = 16;
const IMPLICIT_CD_SAMPLE_RATE_KHZ = 44.1;

export function meetsCutoff(
  parsed: { lossless: boolean; bitDepth?: number; sampleRateKHz?: number; bitrateKbps?: number },
  profile: QualityProfile
): boolean {
  const { cutoff } = profile;
  if (cutoff.lossless && !parsed.lossless) return false;
  if (!cutoff.lossless && parsed.lossless) return true; // lossless dépasse toujours un cutoff lossy

  const effectiveBitDepth = parsed.bitDepth ?? (parsed.lossless ? IMPLICIT_CD_BIT_DEPTH : 0);
  const effectiveSampleRate = parsed.sampleRateKHz ?? (parsed.lossless ? IMPLICIT_CD_SAMPLE_RATE_KHZ : 0);

  if (cutoff.minBitDepth && effectiveBitDepth < cutoff.minBitDepth) return false;
  if (cutoff.minSampleRateKHz && effectiveSampleRate < cutoff.minSampleRateKHz) return false;
  if (cutoff.minBitrateKbps && (parsed.bitrateKbps ?? 0) < cutoff.minBitrateKbps) return false;
  return true;
}
