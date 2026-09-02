import { NextResponse } from "next/server";
import { listMediaFiles } from "@/lib/library/mediaFilesStore";

export const dynamic = "force-dynamic";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const files = listMediaFiles().filter((f) => f.artist.trim().toLowerCase() === id);
  if (files.length === 0) {
    return NextResponse.json({ error: "Artiste introuvable dans la bibliothèque locale" }, { status: 404 });
  }

  const albumsMap = new Map<string, { title: string; trackCount: number }>();
  for (const f of files) {
    if (!f.album) continue;
    const entry = albumsMap.get(f.album) ?? { title: f.album, trackCount: 0 };
    entry.trackCount += 1;
    albumsMap.set(f.album, entry);
  }

  return NextResponse.json({
    id,
    name: files[0].artist,
    trackCount: files.length,
    albums: Array.from(albumsMap.values()).sort((a, b) => a.title.localeCompare(b.title)),
    tracks: files
      .map((f) => ({ id: f.id, title: f.title, album: f.album, durationSeconds: f.durationSeconds }))
      .sort((a, b) => a.title.localeCompare(b.title)),
  });
}
