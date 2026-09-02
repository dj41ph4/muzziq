import { NextResponse } from "next/server";
import { listMediaFiles } from "@/lib/library/mediaFilesStore";

export const dynamic = "force-dynamic";

/** Regroupement des fichiers locaux par album (plan Android §14 — browse album). Même principe que /api/artists. */
export async function GET() {
  const files = listMediaFiles();
  const byAlbum = new Map<string, { title: string; artist: string; trackCount: number }>();

  for (const f of files) {
    if (!f.album) continue;
    const key = `${f.artist.trim().toLowerCase()}::${f.album.trim().toLowerCase()}`;
    const entry = byAlbum.get(key) ?? { title: f.album, artist: f.artist, trackCount: 0 };
    entry.trackCount += 1;
    byAlbum.set(key, entry);
  }

  const albums = Array.from(byAlbum.entries())
    .map(([id, v]) => ({ id, title: v.title, artist: v.artist, trackCount: v.trackCount }))
    .sort((a, b) => a.title.localeCompare(b.title));

  return NextResponse.json({ albums });
}
