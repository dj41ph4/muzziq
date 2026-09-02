import { NextResponse } from "next/server";
import { getPlexConfig } from "@/lib/integrations/plex/store";
import { createPin, buildAuthUrl } from "@/lib/integrations/plex/client";

export const dynamic = "force-dynamic";

/** Étape 1 du flow OAuth PIN : mine un PIN plex.tv et renvoie l'URL à ouvrir (plex.tv/link ou app.plex.tv/auth). */
export async function POST() {
  const config = getPlexConfig(); // mine clientId au besoin
  const pin = await createPin(config.clientId);
  if (!pin) return NextResponse.json({ error: "Impossible de créer un PIN Plex (plex.tv injoignable)" }, { status: 502 });
  return NextResponse.json({ pinId: pin.id, code: pin.code, authUrl: buildAuthUrl(config.clientId, pin.code) });
}
