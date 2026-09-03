import { NextResponse } from "next/server";
import { youtubeMusicProvider } from "@/providers/youtube-music";
import { listMediaFiles } from "@/lib/library/mediaFilesStore";
import { resolveLocalMatch } from "@/lib/identity/resolver";
import type { ExternalTrack } from "@/lib/contracts/music";

export const dynamic = "force-dynamic";

type EnrichedTrack = ExternalTrack & { localMatch?: { fileId: string; confidence: number } };

interface DerivedArtist {
  name: string;
  thumbnailUrl?: string;
  trackCount: number;
}

interface DerivedAlbum {
  title: string;
  artist: string;
  thumbnailUrl?: string;
  trackCount: number;
}

/**
 * Le provider YouTube Music ne renvoie pour l'instant que des morceaux
 * (`searchYoutubeMusic` avec le filtre "Songs" — `albums`/`artists` du
 * SearchResult restent vides, voir src/providers/youtube-music/searchService.ts).
 * Plutôt que d'inventer un faux résultat "Artistes"/"Albums", on dérive des
 * regroupements réels à partir des morceaux effectivement retournés — jamais
 * de donnée fabriquée, juste une agrégation de ce qui existe déjà.
 */
function deriveArtists(tracks: ExternalTrack[]): DerivedArtist[] {
  const byName = new Map<string, DerivedArtist>();
  for (const t of tracks) {
    const key = t.artist.trim().toLowerCase();
    if (!key) continue;
    const existing = byName.get(key);
    if (existing) {
      existing.trackCount += 1;
      if (!existing.thumbnailUrl && t.thumbnailUrl) existing.thumbnailUrl = t.thumbnailUrl;
    } else {
      byName.set(key, { name: t.artist, thumbnailUrl: t.thumbnailUrl, trackCount: 1 });
    }
  }
  return [...byName.values()].sort((a, b) => b.trackCount - a.trackCount);
}

function deriveAlbums(tracks: ExternalTrack[]): DerivedAlbum[] {
  const byKey = new Map<string, DerivedAlbum>();
  for (const t of tracks) {
    if (!t.album) continue;
    const key = `${t.album.trim().toLowerCase()}::${t.artist.trim().toLowerCase()}`;
    const existing = byKey.get(key);
    if (existing) {
      existing.trackCount += 1;
      if (!existing.thumbnailUrl && t.thumbnailUrl) existing.thumbnailUrl = t.thumbnailUrl;
    } else {
      byKey.set(key, { title: t.album, artist: t.artist, thumbnailUrl: t.thumbnailUrl, trackCount: 1 });
    }
  }
  return [...byKey.values()].sort((a, b) => b.trackCount - a.trackCount);
}

export async function GET(req: Request) {
  const { searchParams } = new URL(req.url);
  const q = searchParams.get("q");
  if (!q || q.trim().length === 0) {
    return NextResponse.json({ error: "Paramètre q requis" }, { status: 400 });
  }

  try {
    const result = await youtubeMusicProvider.search({ text: q, scope: "songs" });

    // Availability Engine minimal (plan §10) : pour chaque résultat, vérifier
    // s'il existe déjà en local. C'est le cœur du vertical slice Phase C —
    // "même morceau, source stream + source locale, MuzziQ choisit le local".
    const localFiles = listMediaFiles();
    const tracks: EnrichedTrack[] = result.tracks.map((track) => {
      const match = resolveLocalMatch(track, localFiles);
      return {
        ...track,
        localMatch: match ? { fileId: match.mediaFile.id, confidence: match.confidence } : undefined,
      };
    });

    const artists = result.artists.length > 0 ? result.artists : deriveArtists(tracks);
    const albums = result.albums.length > 0 ? result.albums : deriveAlbums(tracks);

    return NextResponse.json({ tracks, artists, albums });
  } catch (err) {
    // Un provider externe en panne ne doit jamais faire planter la route —
    // toujours une réponse structurée (plan §74/§76).
    return NextResponse.json({ error: "Recherche indisponible", detail: String(err) }, { status: 502 });
  }
}
