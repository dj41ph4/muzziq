import { NextResponse } from "next/server";
import { getPlexConfig } from "@/lib/integrations/plex/store";
import { listPlexServers } from "@/lib/integrations/plex/client";

export const dynamic = "force-dynamic";

/** Serveurs Plex accessibles au compte connecté — remplace la saisie manuelle d'une URL de serveur. */
export async function GET() {
  const config = getPlexConfig();
  if (!config.accountToken) return NextResponse.json({ error: "Compte Plex non connecté" }, { status: 400 });
  const servers = await listPlexServers(config.clientId, config.accountToken);
  return NextResponse.json({ servers });
}
