import { NextResponse } from "next/server";
import { getPlexConfig, updatePlexConfig } from "@/lib/integrations/plex/store";
import { safePlexUrl } from "@/lib/integrations/plex/safeUrl";
import { testPlexConnection } from "@/lib/integrations/plex/client";

export const dynamic = "force-dynamic";

interface SelectBody {
  machineIdentifier: string;
  name: string;
  uri: string;
}

/** Choisit un serveur (et une connexion) parmi ceux renvoyés par GET /servers, teste la connexion avant d'enregistrer. */
export async function POST(req: Request) {
  const body: SelectBody = await req.json();
  if (!body.uri || !body.machineIdentifier) {
    return NextResponse.json({ error: "uri et machineIdentifier requis" }, { status: 400 });
  }
  if (!safePlexUrl(body.uri)) {
    return NextResponse.json({ error: "URL de connexion invalide ou non autorisée" }, { status: 400 });
  }

  const config = getPlexConfig();
  if (!config.accountToken) return NextResponse.json({ error: "Compte Plex non connecté" }, { status: 400 });

  const candidate = { serverUrl: body.uri, token: config.accountToken, clientId: config.clientId };
  const test = await testPlexConnection(candidate);
  if (!test.ok) {
    return NextResponse.json({ error: `Connexion au serveur impossible : ${test.detail}` }, { status: 502 });
  }

  const next = updatePlexConfig({
    serverUrl: body.uri,
    token: config.accountToken,
    machineIdentifier: body.machineIdentifier,
    serverName: body.name,
    musicSections: [], // une nouvelle sélection de serveur invalide le choix de sections précédent
  });
  return NextResponse.json({ ok: true, config: { ...next, token: undefined, accountToken: undefined } });
}
