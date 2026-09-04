import { NextResponse } from "next/server";
import { syncPlexPlaylists } from "@/lib/integrations/plex/playlistSync";

export const dynamic = "force-dynamic";

/** Import de playlists Plex en métadonnées uniquement : jamais un endpoint de lecture. */
export async function POST() {
  return NextResponse.json(await syncPlexPlaylists());
}
