import { innertubeSearch } from "./innertubeClient";
import type { ExternalTrack, SearchQuery, SearchResult } from "@/lib/contracts/music";

/**
 * Parseur de la réponse brute /search (WEB_REMIX). Structure observée en
 * conditions réelles le 2026-09-01 (voir docs/reverse-engineering/youtube-music).
 * Les renderers YouTube changent sans préavis — chaque accès est défensif
 * (optional chaining), un renderer inattendu est simplement ignoré plutôt que
 * de faire planter toute la recherche (règle §74 circuit breaker / §76 résilience).
 */

interface Run {
  text: string;
}

function runsText(runs: Run[] | undefined): string {
  return (runs ?? []).map((r) => r.text).join("");
}

function parseDurationToSeconds(text: string): number | undefined {
  const parts = text.split(":").map((p) => parseInt(p, 10));
  if (parts.some((p) => Number.isNaN(p))) return undefined;
  return parts.reduce((acc, p) => acc * 60 + p, 0);
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractTracksFromShelf(shelf: any): ExternalTrack[] {
  const items = shelf?.contents ?? [];
  const tracks: ExternalTrack[] = [];

  for (const item of items) {
    const renderer = item?.musicResponsiveListItemRenderer;
    if (!renderer) continue;

    const videoId: string | undefined =
      renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
        ?.playNavigationEndpoint?.watchEndpoint?.videoId;
    if (!videoId) continue;

    const titleColumn = renderer.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs;
    const title = runsText(titleColumn) || "Titre inconnu";

    const metaRuns: Run[] =
      renderer.flexColumns?.[1]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?? [];
    // Format observé : Artiste " • " Album " • " Durée. Piège trouvé en test
    // réel : un titre en featuring a PLUSIEURS runs pour l'artiste
    // ("Daft Punk", ", ", "Pharrell Williams", " & ", "Nile Rodgers") avant
    // le vrai séparateur " • " — un split naïf sur chaque run cassait le
    // découpage (l'album affichait juste "," au lieu du vrai nom). Seul le
    // run EXACTEMENT " • " sépare les champs ; tout le reste doit être
    // concaténé dans le groupe courant.
    const groups: string[] = [""];
    for (const run of metaRuns) {
      if (run.text === " • ") {
        groups.push("");
      } else {
        groups[groups.length - 1] += run.text;
      }
    }
    const looksLikeDuration = (t: string) => /^\d+(:\d{2})+$/.test(t.trim());
    const durationText = groups.length > 0 && looksLikeDuration(groups[groups.length - 1] ?? "") ? groups.pop()! : "";
    const artist = groups[0] ?? "Artiste inconnu";
    const album = groups[1] || undefined;

    const thumbnails = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails ?? [];
    const thumbnailUrl = thumbnails[thumbnails.length - 1]?.url;

    tracks.push({
      providerTrackId: videoId,
      provider: "youtube-music",
      title,
      artist,
      album,
      durationSeconds: parseDurationToSeconds(durationText),
      thumbnailUrl,
    });
  }

  return tracks;
}

export async function searchYoutubeMusic(query: SearchQuery): Promise<SearchResult> {
  // "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D" = filtre "Songs" observé sur music.youtube.com.
  const params = query.scope === "songs" || !query.scope ? "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D" : undefined;

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const raw: any = await innertubeSearch(query.text, params);

  const tabs = raw?.contents?.tabbedSearchResultsRenderer?.tabs ?? [];
  const sections = tabs[0]?.tabRenderer?.content?.sectionListRenderer?.contents ?? [];

  const tracks: ExternalTrack[] = [];
  for (const section of sections) {
    const shelf = section?.musicShelfRenderer;
    if (shelf?.title?.runs?.[0]?.text === "Songs") {
      tracks.push(...extractTracksFromShelf(shelf));
    }
  }

  return { tracks, albums: [], artists: [] };
}
