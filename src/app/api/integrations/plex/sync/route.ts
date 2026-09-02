import { NextResponse } from "next/server";
import { syncPlexMusicLibrary } from "@/lib/integrations/plex/librarySync";

export const dynamic = "force-dynamic";

/** Synchronisation de bibliothèque manuelle (pas de scheduler côté MuzziQ pour l'instant — voir CLAUDE.md). */
export async function POST() {
  const summary = await syncPlexMusicLibrary();
  return NextResponse.json(summary);
}
