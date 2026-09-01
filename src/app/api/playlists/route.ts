import { NextResponse } from "next/server";
import { listPlaylists, createPlaylist, listPlaylistItems } from "@/lib/library/playlistsStore";

export const dynamic = "force-dynamic";

export async function GET() {
  const playlists = listPlaylists().map((p) => ({ ...p, itemCount: listPlaylistItems(p.id).length }));
  return NextResponse.json({ playlists });
}

export async function POST(req: Request) {
  const body = await req.json();
  if (!body.name?.trim()) return NextResponse.json({ error: "name requis" }, { status: 400 });
  return NextResponse.json(createPlaylist(body.name.trim()));
}
