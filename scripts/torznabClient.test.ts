import { test } from "node:test";
import assert from "node:assert/strict";
import { parseCapabilities, parseReleases, sanitizeQuery, extractIndexerError } from "@/lib/acquisition/indexers/torznabClient";

/**
 * Fixtures XML Torznab réalistes (plan §92 — contract fixtures) : le
 * protocole est un standard documenté publiquement (spec Torznab/Newznab),
 * ces fixtures sont écrites à la main d'après la spec, pas copiées d'un
 * indexer réel.
 */

const CAPS_XML = `<?xml version="1.0" encoding="UTF-8"?>
<caps>
  <server version="1.1" title="Test Indexer"/>
  <searching>
    <search available="yes" supportedParams="q"/>
    <tv-search available="no" supportedParams="q,season,ep"/>
  </searching>
  <categories>
    <category id="3000" name="Audio">
      <subcat id="3010" name="Audio/MP3"/>
      <subcat id="3040" name="Audio/Lossless"/>
    </category>
  </categories>
</caps>`;

const SEARCH_XML = `<?xml version="1.0" encoding="UTF-8"?>
<rss xmlns:torznab="http://torznab.com/schemas/2015/feed">
  <channel>
    <item>
      <title>Daft Punk - Discovery (2001) FLAC</title>
      <guid>https://example.test/release/123</guid>
      <pubDate>Wed, 15 Jan 2020 10:00:00 +0000</pubDate>
      <enclosure url="https://example.test/dl/123.torrent?token=abc&amp;x=1" length="314572800" type="application/x-bittorrent"/>
      <torznab:attr name="seeders" value="42"/>
      <torznab:attr name="peers" value="50"/>
      <torznab:attr name="infohash" value="ABCDEF0123456789"/>
      <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:ABCDEF0123456789"/>
    </item>
    <item>
      <title>Linkin Park - Meteora [24bit 96kHz]</title>
      <guid>https://example.test/release/456</guid>
      <pubDate>Fri, 01 Mar 2024 08:30:00 +0000</pubDate>
      <torznab:attr name="seeders" value="7"/>
      <torznab:attr name="peers" value="9"/>
    </item>
  </channel>
</rss>`;

const ERROR_XML = `<?xml version="1.0" encoding="UTF-8"?>
<error code="100" description="Invalid API Key"/>`;

test("parseCapabilities lit search disponible et les catégories/sous-catégories", () => {
  const caps = parseCapabilities(CAPS_XML);
  assert.equal(caps.search, true);
  assert.equal(caps.categories.length, 1);
  assert.equal(caps.categories[0].id, 3000);
  assert.equal(caps.categories[0].name, "Audio");
});

test("parseReleases extrait titre, taille, seeders/leechers, magnet, infoHash", () => {
  const releases = parseReleases(SEARCH_XML, "ix1", "Test Indexer");
  assert.equal(releases.length, 2);

  const first = releases[0];
  assert.equal(first.title, "Daft Punk - Discovery (2001) FLAC");
  assert.equal(first.size, 314572800);
  assert.equal(first.seeders, 42);
  assert.equal(first.leechers, 8); // peers(50) - seeders(42)
  assert.equal(first.infoHash, "ABCDEF0123456789");
  assert.equal(first.magnetUrl, "magnet:?xt=urn:btih:ABCDEF0123456789");
  // L'URL d'enclosure contient un "&amp;" échappé — doit être décodé, pas laissé tel quel.
  assert.ok(first.downloadUrl?.includes("&x=1"), `downloadUrl mal décodé: ${first.downloadUrl}`);
  assert.ok(!first.downloadUrl?.includes("&amp;"), "downloadUrl contient encore une entité XML non décodée");
});

test("parseReleases : seeders/leechers absents restent null, jamais 0 par défaut", () => {
  const releases = parseReleases(SEARCH_XML, "ix1", "Test Indexer");
  const second = releases[1];
  assert.equal(second.seeders, 7);
  assert.equal(second.magnetUrl, null);
  assert.equal(second.infoHash, null);
});

test("extractIndexerError détecte une réponse d'erreur Torznab", () => {
  assert.equal(extractIndexerError(ERROR_XML), "Invalid API Key");
  assert.equal(extractIndexerError(SEARCH_XML), null);
});

test("sanitizeQuery remplace espaces par points et retire les accents", () => {
  assert.equal(sanitizeQuery("Daft Punk Discovery"), "Daft.Punk.Discovery");
  assert.equal(sanitizeQuery("Événement Été"), "Evenement.Ete");
});
