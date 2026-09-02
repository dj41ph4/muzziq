import { NextResponse } from "next/server";
import { importPlexListenHistory } from "@/lib/integrations/plex/historySync";

export const dynamic = "force-dynamic";

export async function POST() {
  const summary = await importPlexListenHistory();
  return NextResponse.json(summary);
}
