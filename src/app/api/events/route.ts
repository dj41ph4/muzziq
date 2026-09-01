import { NextResponse } from "next/server";
import { recordEvent, listRecentEvents } from "@/lib/history/playbackEventsStore";
import { listRecordings } from "@/lib/library/recordingsStore";
import { findOrCreateRecordingFromExternal } from "@/lib/library/recordingResolution";

export const dynamic = "force-dynamic";

export async function GET() {
  const events = listRecentEvents(50);
  const recordings = listRecordings();
  const enriched = events.map((e) => ({ ...e, recording: recordings.find((r) => r.id === e.recordingId) }));
  return NextResponse.json({ events: enriched });
}

/**
 * Un PLAY_START doit exister dans le catalogue MUZZIK même si l'utilisateur
 * n'a jamais cliqué "+ Bibliothèque" (plan §17 — catalogue ≠ bibliothèque) :
 * on résout/crée le Recording à la volée avec le même helper que l'ajout
 * explicite, sans jamais créer de LibraryItem depuis ce endpoint.
 */
export async function POST(req: Request) {
  const body = await req.json();
  if (!body.type || !body.source || !body.provider || !body.providerTrackId || !body.title || !body.artist) {
    return NextResponse.json({ error: "Champs requis manquants" }, { status: 400 });
  }
  const recording = findOrCreateRecordingFromExternal(body);
  const event = recordEvent({ recordingId: recording.id, type: body.type, source: body.source });
  return NextResponse.json({ ...event, recording });
}
