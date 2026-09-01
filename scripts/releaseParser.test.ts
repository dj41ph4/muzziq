import { test } from "node:test";
import assert from "node:assert/strict";
import { parseReleaseTitle } from "@/lib/acquisition/releaseParser";

/**
 * Golden tests du parser (plan §93) — les exemples viennent directement du
 * plan d'architecture §24. Chaque échec corrigé doit enrichir cette suite,
 * jamais juste être corrigé silencieusement.
 */

test("Artist - Album (2024) FLAC", () => {
  const r = parseReleaseTitle("Daft Punk - Discovery (2001) FLAC");
  assert.equal(r.artist, "Daft Punk");
  assert.equal(r.album, "Discovery");
  assert.equal(r.year, 2001);
  assert.equal(r.codec, "FLAC");
  assert.equal(r.lossless, true);
});

test("Artist - Album [24bit 96kHz]", () => {
  const r = parseReleaseTitle("Linkin Park - Meteora [24bit 96kHz]");
  assert.equal(r.artist, "Linkin Park");
  assert.equal(r.album, "Meteora");
  assert.equal(r.bitDepth, 24);
  assert.equal(r.sampleRateKHz, 96);
});

test("Artist - Album Deluxe Edition WEB FLAC", () => {
  const r = parseReleaseTitle("Taylor Swift - 1989 Deluxe Edition WEB FLAC");
  assert.equal(r.artist, "Taylor Swift");
  assert.equal(r.source, "WEB");
  assert.equal(r.codec, "FLAC");
  assert.equal(r.edition, "Deluxe");
});

test("Artist - Album CD-FLAC", () => {
  const r = parseReleaseTitle("Pink Floyd - The Wall CD-FLAC");
  assert.equal(r.artist, "Pink Floyd");
  assert.equal(r.codec, "FLAC");
  // "CD-FLAC" ne doit pas être compté comme 2 disques.
  assert.equal(r.discCount, undefined);
});

test("Artist - Album 2CD", () => {
  const r = parseReleaseTitle("Pink Floyd - The Wall 2CD");
  assert.equal(r.discCount, 2);
});

test("Artist - Album Remastered", () => {
  const r = parseReleaseTitle("Fleetwood Mac - Rumours Remastered");
  assert.equal(r.remastered, true);
});

test("Artist - Album 320kbps", () => {
  const r = parseReleaseTitle("Daft Punk - Random Access Memories 320kbps");
  assert.equal(r.bitrateKbps, 320);
  assert.equal(r.lossless, false);
});

test("Artist Discography 1999-2025 FLAC — pas de faux positif sur la plage d'années", () => {
  const r = parseReleaseTitle("Daft Punk Discography 1999-2025 FLAC");
  assert.equal(r.codec, "FLAC");
  // Le premier nombre à 4 chiffres plausible est retenu — comportement
  // documenté, pas une plage d'années réellement interprétée (pas de
  // vrai besoin de le faire tant qu'aucun candidat réel ne l'exige).
  assert.equal(r.year, 1999);
});

test("champ non détecté reste undefined, jamais deviné", () => {
  const r = parseReleaseTitle("Some Random Release Name Without Metadata");
  assert.equal(r.codec, undefined);
  assert.equal(r.year, undefined);
  assert.equal(r.bitDepth, undefined);
});
