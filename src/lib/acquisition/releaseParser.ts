/**
 * Music Release Parser (plan §24). Le parser cinéma de Movviz ne suffit pas
 * pour la musique — nomenclature différente (bit depth/sample rate, CD
 * count, éditions). Réimplémentation indépendante, pas un portage direct.
 *
 * Volontairement permissif : un champ non détecté reste `undefined` plutôt
 * que de deviner. Le Scorer (releaseScorer.ts) pénalise l'incertitude,
 * le Parser ne doit jamais fabriquer une valeur plausible mais fausse.
 */

export interface ParsedRelease {
  artist?: string;
  album?: string;
  year?: number;
  edition?: string;
  remastered: boolean;
  source?: "WEB" | "CD" | "VINYL" | "DVD";
  codec?: "FLAC" | "ALAC" | "APE" | "WAV" | "MP3" | "AAC" | "OGG";
  lossless: boolean;
  bitDepth?: number;
  sampleRateKHz?: number;
  bitrateKbps?: number;
  discCount?: number;
  trackCount?: number;
}

const EDITION_KEYWORDS = [
  "deluxe",
  "expanded",
  "anniversary",
  "bonus",
  "special edition",
  "limited edition",
  "collector",
  "remastered edition",
];

const LOSSLESS_CODECS = new Set(["FLAC", "ALAC", "APE", "WAV"]);

export function parseReleaseTitle(rawTitle: string): ParsedRelease {
  const title = rawTitle.trim();
  const lower = title.toLowerCase();

  const result: ParsedRelease = { remastered: false, lossless: false };

  // Année : le premier "(19xx)"/"(20xx)" ou nombre isolé à 4 chiffres plausible.
  const yearMatch = title.match(/\b(19[5-9]\d|20[0-4]\d)\b/);
  if (yearMatch) result.year = parseInt(yearMatch[1], 10);

  // Codec — ordre important : ALAC/APE avant un éventuel faux positif "AAC".
  if (/\bflac\b/i.test(title)) result.codec = "FLAC";
  else if (/\balac\b/i.test(title)) result.codec = "ALAC";
  else if (/\bape\b/i.test(title)) result.codec = "APE";
  else if (/\bwav\b/i.test(title)) result.codec = "WAV";
  else if (/\bmp3\b/i.test(title)) result.codec = "MP3";
  else if (/\baac\b/i.test(title)) result.codec = "AAC";
  else if (/\bogg\b/i.test(title)) result.codec = "OGG";
  if (result.codec) result.lossless = LOSSLESS_CODECS.has(result.codec);

  // Bit depth / sample rate — formats observés : "24bit", "24-bit", "[24bit 96kHz]", "96kHz", "44.1kHz"
  const bitDepthMatch = title.match(/\b(16|24|32)[\s-]?bit\b/i);
  if (bitDepthMatch) result.bitDepth = parseInt(bitDepthMatch[1], 10);

  const sampleRateMatch = title.match(/\b(44\.1|48|88\.2|96|176\.4|192)\s?k?hz\b/i);
  if (sampleRateMatch) result.sampleRateKHz = parseFloat(sampleRateMatch[1]);

  const bitrateMatch = title.match(/\b(\d{2,3})\s?kbps\b/i);
  if (bitrateMatch) result.bitrateKbps = parseInt(bitrateMatch[1], 10);

  // Source
  if (/\bweb\b/i.test(title)) result.source = "WEB";
  else if (/\bvinyl\b/i.test(title)) result.source = "VINYL";
  else if (/\bdvd\b/i.test(title)) result.source = "DVD";
  else if (/\bcd\b/i.test(title)) result.source = "CD";

  // Remaster
  result.remastered = /\bremaster(ed)?\b/i.test(title);

  // Édition
  const editionHit = EDITION_KEYWORDS.find((kw) => lower.includes(kw));
  if (editionHit) {
    result.edition = editionHit.replace(/\b\w/g, (c) => c.toUpperCase());
  }

  // Multi-disque : "2CD", "3xCD" — distinct du "CD" isolé utilisé comme source.
  const discMatch = title.match(/\b(\d)\s?x?\s?cd\b/i);
  if (discMatch) result.discCount = parseInt(discMatch[1], 10);

  // Nombre de pistes si explicite : "(12 tracks)"
  const trackCountMatch = title.match(/\((\d{1,2})\s?tracks?\)/i);
  if (trackCountMatch) result.trackCount = parseInt(trackCountMatch[1], 10);

  // Artiste / Album : convention scène la plus commune "Artist - Album ...".
  // On coupe au premier token de métadonnée reconnu (année, codec, etc.)
  // pour ne pas polluer le nom d'album avec "FLAC 24bit 96kHz".
  const dashSplit = title.split(/\s-\s/);
  if (dashSplit.length >= 2) {
    result.artist = dashSplit[0].trim();
    result.album = stripMetadataTokens(dashSplit.slice(1).join(" - ")).trim() || undefined;
  } else {
    result.album = stripMetadataTokens(title).trim() || undefined;
  }

  return result;
}

function stripMetadataTokens(text: string): string {
  return text
    .replace(/\(\d{4}\)/g, "")
    .replace(/\[[^\]]*\]/g, "")
    .replace(/\b(flac|alac|ape|wav|mp3|aac|ogg)\b/gi, "")
    .replace(/\b(16|24|32)[\s-]?bit\b/gi, "")
    .replace(/\b(44\.1|48|88\.2|96|176\.4|192)\s?k?hz\b/gi, "")
    .replace(/\b\d{2,3}\s?kbps\b/gi, "")
    .replace(/\bweb\b/gi, "")
    .replace(/\bcd-?flac\b/gi, "")
    .replace(/\b\d\s?x?\s?cd\b/gi, "")
    .replace(/\bremaster(ed)?\b/gi, "")
    .replace(/\s{2,}/g, " ")
    .trim();
}
