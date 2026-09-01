import { NextResponse } from "next/server";
import { getPlaylist, deletePlaylist, listPlaylistItems } from "@/lib/library/playlistsStore";
import { listRecordings } from "@/lib/library/recordingsStore";

export const dynamic = "force-dynamic";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const playlist = getPlaylist(id);
  if (!playlist) return NextResponse.json({ error: "Introuvable" }, { status: 404 });
  const recordings = listRecordings();
  const items = listPlaylistItems(id).map((i) => ({ ...i, recording: recordings.find((r) => r.id === i.recordingId) }));
  return NextResponse.json({ ...playlist, items });
}

export async function DELETE(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const removed = deletePlaylist(id);
  if (!removed) return NextResponse.json({ error: "Introuvable" }, { status: 404 });
  return NextResponse.json({ ok: true });
}
