import { NextResponse } from "next/server";
import { addTorrent, listTorrents } from "@/lib/acquisition/torrent/webTorrentBackend";
import { importCompletedTorrent } from "@/lib/acquisition/torrent/importCompleted";

export const dynamic = "force-dynamic";

export async function GET() {
  const torrents = await listTorrents();
  return NextResponse.json({ torrents });
}

export async function POST(req: Request) {
  const body = await req.json();
  if (!body.magnetOrUrl) return NextResponse.json({ error: "magnetOrUrl requis" }, { status: 400 });

  try {
    const summary = await addTorrent(body.magnetOrUrl, (t) => {
      importCompletedTorrent(t).catch((e) => console.error("[downloads] import échoué:", e));
    });
    return NextResponse.json(summary);
  } catch (e) {
    return NextResponse.json({ error: e instanceof Error ? e.message : String(e) }, { status: 502 });
  }
}
