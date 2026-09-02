import { NextResponse } from "next/server";
import { listMediaFiles } from "@/lib/library/mediaFilesStore";

export const dynamic = "force-dynamic";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const files = listMediaFiles().filter((f) => {
    const key = `${f.artist.trim().toLowerCase()}::${(f.album ?? "").trim().toLowerCase()}`;
    return key === id;
  });
  if (files.length === 0) {
    return NextResponse.json({ error: "Album introuvable dans la bibliothèque locale" }, { status: 404 });
  }

  return NextResponse.json({
    id,
    title: files[0].album,
    artist: files[0].artist,
    tracks: files
      .map((f) => ({ id: f.id, title: f.title, trackNumber: f.trackNumber, durationSeconds: f.durationSeconds }))
      .sort((a, b) => (a.trackNumber ?? 0) - (b.trackNumber ?? 0)),
  });
}
