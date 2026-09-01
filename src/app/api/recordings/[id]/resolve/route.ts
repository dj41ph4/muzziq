import { NextResponse } from "next/server";
import { resolveRecordingPlayback } from "@/lib/library/recordingPlayback";

export const dynamic = "force-dynamic";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const resolved = resolveRecordingPlayback(id);
  if (!resolved) return NextResponse.json({ error: "Aucune source disponible pour ce morceau" }, { status: 404 });
  return NextResponse.json(resolved);
}
