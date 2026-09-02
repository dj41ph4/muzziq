import { NextResponse } from "next/server";
import { getPlexConfig } from "@/lib/integrations/plex/store";
import { getMusicSections } from "@/lib/integrations/plex/client";

export const dynamic = "force-dynamic";

/** Bibliothèques de type "artist" (musique) du serveur choisi. */
export async function GET() {
  const config = getPlexConfig();
  if (!config.serverUrl || !config.token) return NextResponse.json({ error: "Serveur Plex non connecté" }, { status: 400 });
  const sections = await getMusicSections(config);
  return NextResponse.json({ sections, selected: config.musicSections });
}
