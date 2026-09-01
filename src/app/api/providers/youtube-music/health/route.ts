import { NextResponse } from "next/server";
import { youtubeMusicProvider } from "@/providers/youtube-music";

export const dynamic = "force-dynamic";

export async function GET() {
  const report = await youtubeMusicProvider.health();
  return NextResponse.json(report);
}
