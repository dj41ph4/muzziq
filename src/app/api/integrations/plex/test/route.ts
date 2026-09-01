import { NextResponse } from "next/server";
import { getPlexConfig, updatePlexConfig } from "@/lib/integrations/plex/store";
import { testPlexConnection } from "@/lib/integrations/plex/client";

export const dynamic = "force-dynamic";

export async function POST() {
  const config = getPlexConfig();
  const result = await testPlexConnection(config);
  updatePlexConfig({ lastTest: { ...result, at: Date.now() } });
  return NextResponse.json(result);
}
