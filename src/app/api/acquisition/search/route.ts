import { NextResponse } from "next/server";
import { listIndexers } from "@/lib/acquisition/indexers/store";
import { searchIndexer } from "@/lib/acquisition/indexers/torznabClient";
import { parseReleaseTitle } from "@/lib/acquisition/releaseParser";
import { scoreRelease } from "@/lib/acquisition/releaseScorer";
import { QUALITY_PROFILES, type QualityProfileName } from "@/lib/acquisition/qualityProfiles";

export const dynamic = "force-dynamic";

/**
 * SearchCoordinator (plan §20) : interroge chaque indexer activé, parse
 * chaque titre de release (§24), score contre la cible demandée (§25) avec
 * le profil de qualité choisi, trie par score. Aucun grab ici — uniquement
 * la recherche + le classement, l'utilisateur décide ensuite (§79, jamais
 * un téléchargement automatique caché derrière une recherche).
 */
export async function POST(req: Request) {
  const body = await req.json();
  const { artist, album, year, expectedTrackCount, profile } = body as {
    artist: string;
    album: string;
    year?: number;
    expectedTrackCount?: number;
    profile?: QualityProfileName;
  };
  if (!artist || !album) {
    return NextResponse.json({ error: "artist et album requis" }, { status: 400 });
  }

  const qualityProfile = QUALITY_PROFILES[profile ?? "lossless"];
  const indexers = listIndexers().filter((ix) => ix.enabled);
  if (indexers.length === 0) {
    return NextResponse.json({ error: "Aucun indexer configuré — voir /settings/indexers", candidates: [] }, { status: 400 });
  }

  const query = `${artist} ${album}`;
  const results = await Promise.allSettled(indexers.map((ix) => searchIndexer(ix, query)));

  const candidates = results
    .flatMap((r) => (r.status === "fulfilled" ? r.value : []))
    .map((release) => {
      const parsed = parseReleaseTitle(release.title);
      const { score, reasons } = scoreRelease(
        { parsed, seeders: release.seeders ?? 0, publishedAt: release.publishDate ? new Date(release.publishDate) : undefined },
        { artist, album, year, expectedTrackCount },
        qualityProfile
      );
      return { release, parsed, score, reasons };
    })
    .sort((a, b) => b.score - a.score);

  return NextResponse.json({ candidates, indexersQueried: indexers.length });
}
