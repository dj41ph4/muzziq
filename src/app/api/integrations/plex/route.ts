import { NextResponse } from "next/server";
import { getPlexConfig, updatePlexConfig } from "@/lib/integrations/plex/store";

export const dynamic = "force-dynamic";

export async function GET() {
  const config = getPlexConfig();
  // Jamais le token en clair une fois enregistré.
  return NextResponse.json({ ...config, token: config.token ? `••••${config.token.slice(-4)}` : "" });
}

export async function PATCH(req: Request) {
  const body = await req.json();
  const next = updatePlexConfig(body);
  return NextResponse.json(next);
}
