import { NextResponse } from "next/server";
import { getPlexConfig, updatePlexConfig } from "@/lib/integrations/plex/store";

export const dynamic = "force-dynamic";

function mask(secret: string): string {
  return secret ? `••••${secret.slice(-4)}` : "";
}

export async function GET() {
  const config = getPlexConfig();
  // Jamais les tokens en clair une fois enregistrés.
  return NextResponse.json({
    ...config,
    token: mask(config.token),
    accountToken: config.accountToken ? mask(config.accountToken) : undefined,
  });
}

export async function PATCH(req: Request) {
  const body = await req.json();
  const next = updatePlexConfig(body);
  return NextResponse.json({
    ...next,
    token: mask(next.token),
    accountToken: next.accountToken ? mask(next.accountToken) : undefined,
  });
}
