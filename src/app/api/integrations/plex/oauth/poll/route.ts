import { NextResponse } from "next/server";
import { getPlexConfig, updatePlexConfig } from "@/lib/integrations/plex/store";
import { checkPin, getPlexAccount } from "@/lib/integrations/plex/client";

export const dynamic = "force-dynamic";

/** Étape 2 : le client poll cet endpoint après avoir envoyé l'utilisateur sur authUrl. */
export async function GET(req: Request) {
  const pinId = Number(new URL(req.url).searchParams.get("pinId"));
  if (!Number.isFinite(pinId)) return NextResponse.json({ error: "pinId requis" }, { status: 400 });

  const config = getPlexConfig();
  const token = await checkPin(config.clientId, pinId);
  if (!token) return NextResponse.json({ status: "pending" });

  const account = await getPlexAccount(config.clientId, token);
  if (!account) return NextResponse.json({ status: "pending" });

  updatePlexConfig({ accountToken: account.authToken, accountUsername: account.username });
  return NextResponse.json({ status: "authorized", account: { username: account.username, email: account.email, thumb: account.thumb } });
}
