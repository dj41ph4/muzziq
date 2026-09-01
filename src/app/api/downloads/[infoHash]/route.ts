import { NextResponse } from "next/server";
import { pauseTorrent, resumeTorrent, removeTorrent } from "@/lib/acquisition/torrent/webTorrentBackend";

export const dynamic = "force-dynamic";

export async function PATCH(req: Request, { params }: { params: Promise<{ infoHash: string }> }) {
  const { infoHash } = await params;
  const body = await req.json();
  const ok = body.action === "pause" ? await pauseTorrent(infoHash) : body.action === "resume" ? await resumeTorrent(infoHash) : false;
  if (!ok) return NextResponse.json({ error: "Introuvable ou action invalide" }, { status: 404 });
  return NextResponse.json({ ok: true });
}

export async function DELETE(req: Request, { params }: { params: Promise<{ infoHash: string }> }) {
  const { infoHash } = await params;
  const { searchParams } = new URL(req.url);
  const deleteData = searchParams.get("deleteData") === "true";
  const ok = await removeTorrent(infoHash, deleteData);
  if (!ok) return NextResponse.json({ error: "Introuvable" }, { status: 404 });
  return NextResponse.json({ ok: true });
}
