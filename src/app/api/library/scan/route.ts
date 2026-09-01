import { NextResponse } from "next/server";
import { scanMusicDir } from "@/lib/library/scanner";
import { getSettings } from "@/lib/settings/store";

export const dynamic = "force-dynamic";

export async function POST() {
  const { musicDir } = getSettings();
  if (!musicDir) {
    return NextResponse.json({ error: "Aucun dossier musique configuré (Settings.musicDir)" }, { status: 400 });
  }
  const summary = await scanMusicDir(musicDir);
  return NextResponse.json(summary);
}
