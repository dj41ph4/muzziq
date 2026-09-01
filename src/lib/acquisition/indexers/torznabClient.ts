import type { ConfiguredIndexer, IndexerCapabilities, IndexerRelease } from "./types";

/**
 * Client Torznab — réimplémentation indépendante (protocole RSS/XML standard,
 * même mécanique d'extraction que le client Movviz : pas de dépendance XML,
 * juste des regex ciblées sur une structure connue). Les fonctions de parsing
 * sont pures et exportées séparément du réseau pour être testables avec des
 * fixtures XML (plan §92) sans jamais avoir besoin d'un indexer réel.
 */

export function decodeXmlEntities(s: string): string {
  return s
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, code) => String.fromCharCode(Number(code)))
    .replace(/&#x([0-9a-f]+);/gi, (_, code) => String.fromCharCode(parseInt(code, 16)))
    .replace(/&amp;/g, "&");
}

const attr = (block: string, name: string) => {
  const v = block.match(new RegExp(`attr[^>]*name="${name}"[^>]*value="([^"]*)"`, "i"))?.[1];
  return v != null ? decodeXmlEntities(v) : null;
};
const tagOf = (block: string, name: string) => {
  const v = block.match(new RegExp(`<${name}[^>]*>([\\s\\S]*?)</${name}>`, "i"))?.[1]?.trim();
  return v != null ? decodeXmlEntities(v) : null;
};

export function parseCapabilities(xml: string): IndexerCapabilities {
  const searchBlock = xml.match(/<search\b[^>]*\/?>/i)?.[0] ?? "";
  const search = /available="yes"/i.test(searchBlock);

  const categories: { id: number; name: string }[] = [];
  const catBlock = xml.match(/<categories>[\s\S]*?<\/categories>/i)?.[0] ?? "";
  const catRe = /<category\b[^>]*?(?:\/>|>[\s\S]*?<\/category>)/gi;
  let m: RegExpExecArray | null;
  while ((m = catRe.exec(catBlock))) {
    const id = Number(m[0].match(/\bid="(\d+)"/i)?.[1]);
    const name = m[0].match(/\bname="([^"]*)"/i)?.[1];
    if (id && name) categories.push({ id, name: decodeXmlEntities(name) });
  }
  return { search, categories };
}

export function extractIndexerError(xml: string): string | null {
  if (!/<error[\s/>]/i.test(xml)) return null;
  const desc = xml.match(/description="([^"]*)"/i)?.[1];
  const code = xml.match(/code="(\d+)"/i)?.[1];
  return desc ? decodeXmlEntities(desc) : code ? `erreur ${code}` : "erreur indexeur";
}

const safeDate = (v: string | null) => {
  if (!v) return null;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
};

export function parseReleases(xml: string, indexerId: string, indexerName: string): IndexerRelease[] {
  const items = xml.match(/<item[\s>][\s\S]*?<\/item>/gi) ?? [];
  const releases: IndexerRelease[] = [];
  for (const block of items) {
    const title = tagOf(block, "title");
    if (!title) continue;
    const enclosureRaw = block.match(/<enclosure[^>]*url="([^"]*)"[^>]*>/i)?.[1] ?? null;
    const enclosure = enclosureRaw != null ? decodeXmlEntities(enclosureRaw) : null;
    const enclosureLen = block.match(/<enclosure[^>]*length="(\d+)"/i)?.[1];
    const size = Number(attr(block, "size") ?? tagOf(block, "size") ?? enclosureLen ?? 0);
    const seeders = attr(block, "seeders");
    const peers = attr(block, "peers") ?? attr(block, "leechers");
    const magnet = attr(block, "magneturl");
    const infoHash = attr(block, "infohash");
    const guid = tagOf(block, "guid") ?? enclosure ?? magnet ?? title;

    releases.push({
      guid,
      title,
      indexerId,
      indexer: indexerName,
      size: Number.isFinite(size) ? size : 0,
      seeders: seeders != null ? Number(seeders) : null,
      leechers: peers != null && seeders != null ? Math.max(0, Number(peers) - Number(seeders)) : null,
      publishDate: safeDate(tagOf(block, "pubDate")),
      downloadUrl: enclosure,
      magnetUrl: magnet,
      infoHash,
    });
  }
  return releases;
}

/**
 * Une release scène sépare les mots par des points, pas des espaces — un
 * espace littéral dans la requête ne matche pas un point dans le nom réel
 * (confirmé par Movviz sur plusieurs indexers réels). Accents/apostrophes
 * retirés pour la même raison : jamais présents tels quels dans un nom de
 * release.
 */
export function sanitizeQuery(q: string): string {
  return q
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/['’‘]/g, " ")
    .replace(/[^a-zA-Z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\s+/g, ".");
}

function buildUrl(ix: ConfiguredIndexer, params: Record<string, string>): string {
  const url = new URL(ix.baseUrl);
  if (ix.authType === "apikey" && ix.apiKey) url.searchParams.set("apikey", ix.apiKey);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  return url.toString();
}

function authHeaders(ix: ConfiguredIndexer): Record<string, string> {
  if (ix.authType === "credentials" && ix.username) {
    const token = Buffer.from(`${ix.username}:${ix.password}`).toString("base64");
    return { authorization: `Basic ${token}` };
  }
  if (ix.authType === "x-api-key" && ix.apiKey) return { "X-Api-Key": ix.apiKey };
  return {};
}

async function fetchXml(url: string, ix: ConfiguredIndexer, timeoutMs = 12000): Promise<{ ok: boolean; status: number; text: string }> {
  const res = await fetch(url, { headers: authHeaders(ix), signal: AbortSignal.timeout(timeoutMs), cache: "no-store" });
  return { ok: res.ok, status: res.status, text: await res.text() };
}

export async function testIndexer(ix: ConfiguredIndexer): Promise<{ ok: boolean; detail: string; caps: IndexerCapabilities | null }> {
  try {
    const { ok, status, text } = await fetchXml(buildUrl(ix, { t: "caps" }), ix);
    if (!ok) return { ok: false, detail: `HTTP ${status}`, caps: null };
    const error = extractIndexerError(text);
    if (error) return { ok: false, detail: error, caps: null };
    if (!/<caps[\s>]/i.test(text)) return { ok: false, detail: "réponse inattendue (pas un indexer Torznab ?)", caps: null };
    return { ok: true, detail: "OK", caps: parseCapabilities(text) };
  } catch (e) {
    return { ok: false, detail: e instanceof Error && e.name === "AbortError" ? "timeout" : String(e), caps: null };
  }
}

export async function searchIndexer(ix: ConfiguredIndexer, query: string): Promise<IndexerRelease[]> {
  const params: Record<string, string> = { t: "search", q: sanitizeQuery(query) };
  if (ix.categories.length) params.cat = ix.categories.join(",");
  const { ok, text } = await fetchXml(buildUrl(ix, params), ix);
  if (!ok) return [];
  const error = extractIndexerError(text);
  if (error) throw new Error(error);
  return parseReleases(text, ix.id, ix.name);
}
