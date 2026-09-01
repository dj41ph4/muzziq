import { test } from "node:test";
import assert from "node:assert/strict";
import { parseReleaseTitle } from "@/lib/acquisition/releaseParser";
import { scoreRelease } from "@/lib/acquisition/releaseScorer";
import { QUALITY_PROFILES, meetsCutoff } from "@/lib/acquisition/qualityProfiles";

const target = { artist: "Linkin Park", album: "Meteora", year: 2003, expectedTrackCount: 13 };

test("bon candidat FLAC exact — score élevé, positif", () => {
  const parsed = parseReleaseTitle("Linkin Park - Meteora (2003) FLAC");
  const { score, reasons } = scoreRelease({ parsed, seeders: 20 }, target, QUALITY_PROFILES.lossless);
  assert.ok(score > 100, `score trop bas: ${score} (${reasons.join(", ")})`);
});

test("mauvais artiste — pénalité écrasante, jamais un candidat retenu par erreur", () => {
  const parsed = parseReleaseTitle("Justin Bieber - Meteora (2003) FLAC");
  const { score } = scoreRelease({ parsed, seeders: 20 }, target, QUALITY_PROFILES.lossless);
  assert.ok(score < 0, `un mauvais artiste doit rendre le score négatif, obtenu ${score}`);
});

test("mauvais album — pénalité écrasante", () => {
  const parsed = parseReleaseTitle("Linkin Park - Hybrid Theory (2000) FLAC");
  const { score } = scoreRelease({ parsed, seeders: 20 }, target, QUALITY_PROFILES.lossless);
  assert.ok(score < 0, `un mauvais album doit rendre le score négatif, obtenu ${score}`);
});

test("transcodage suspect (FLAC depuis un 128kbps) — pénalisé, pas juste ignoré", () => {
  const cleanParsed = parseReleaseTitle("Linkin Park - Meteora (2003) FLAC");
  const suspiciousParsed = parseReleaseTitle("Linkin Park - Meteora (2003) FLAC 128kbps");
  const clean = scoreRelease({ parsed: cleanParsed, seeders: 20 }, target, QUALITY_PROFILES.lossless);
  const suspicious = scoreRelease({ parsed: suspiciousParsed, seeders: 20 }, target, QUALITY_PROFILES.lossless);
  assert.ok(suspicious.score < clean.score, "un transcodage suspect doit scorer moins qu'un FLAC propre");
});

test("compilation quand un studio album est voulu n'obtient pas le bonus album exact", () => {
  const parsed = parseReleaseTitle("Linkin Park - Greatest Hits (2020) FLAC");
  const { score } = scoreRelease({ parsed, seeders: 20 }, target, QUALITY_PROFILES.lossless);
  const exact = scoreRelease(
    { parsed: parseReleaseTitle("Linkin Park - Meteora (2003) FLAC"), seeders: 20 },
    target,
    QUALITY_PROFILES.lossless
  );
  assert.ok(score < exact.score);
});

test("cutoff lossless : un FLAC 16/44.1 satisfait le profil Lossless mais pas Hi-Res", () => {
  const parsed = parseReleaseTitle("Artist - Album (2020) FLAC");
  assert.equal(meetsCutoff(parsed, QUALITY_PROFILES.lossless), true);
  assert.equal(meetsCutoff(parsed, QUALITY_PROFILES.hires), false);
});

test("cutoff hires : un FLAC 24bit satisfait Hi-Res", () => {
  const parsed = parseReleaseTitle("Artist - Album [24bit 96kHz] FLAC");
  assert.equal(meetsCutoff(parsed, QUALITY_PROFILES.hires), true);
});
