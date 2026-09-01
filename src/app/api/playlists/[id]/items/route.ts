import { NextResponse } from "next/server";
import { addPlaylistItem, removePlaylistItem } from "@/lib/library/playlistsStore";

export const dynamic = "force-dynamic";

export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const body = await req.json();
  if (!body.recordingId) return NextResponse.json({ error: "recordingId requis" }, { status: 400 });
  return NextResponse.json(addPlaylistItem(id, body.recordingId));
}

export async function DELETE(req: Request) {
  const { searchParams } = new URL(req.url);
  const itemId = searchParams.get("itemId");
  if (!itemId) return NextResponse.json({ error: "itemId requis" }, { status: 400 });
  const removed = removePlaylistItem(itemId);
  if (!removed) return NextResponse.json({ error: "Introuvable" }, { status: 404 });
  return NextResponse.json({ ok: true });
}
