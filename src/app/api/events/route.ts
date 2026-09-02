import { NextResponse } from "next/server";
import { recordEvent, listRecentEvents } from "@/lib/history/playbackEventsStore";
import { listRecordings } from "@/lib/library/recordingsStore";
import { findOrCreateRecordingFromExternal } from "@/lib/library/recordingResolution";
import { recordPlaybackStartedContext } from "@/lib/userContext/ingest";
import { adjustArtistAffinity } from "@/lib/userContext/preferences";
import { DEFAULT_USER_ID } from "@/lib/userContext/types";
import { exportPlexScrobble } from "@/lib/integrations/plex/historySync";

export const dynamic = "force-dynamic";

export async function GET() {
  const events = listRecentEvents(50);
  const recordings = listRecordings();
  const enriched = events.map((e) => ({ ...e, recording: recordings.find((r) => r.id === e.recordingId) }));
  return NextResponse.json({ events: enriched });
}

/**
 * Un PLAY_START doit exister dans le catalogue MuzziQ même si l'utilisateur
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

  // Additif — jamais bloquant : le Context Engine SQLite (plan §45, porté de
  // Movviz) alimente le contexte MuzziQ AI en plus du store JSON existant,
  // jamais à sa place. Une DB indisponible/désactivée ne doit jamais casser
  // la lecture (withUserContextDb dégrade déjà en no-op silencieux).
  if (body.type === "PLAY_START") {
    await recordPlaybackStartedContext({
      userId: DEFAULT_USER_ID,
      recordingId: recording.id,
      title: recording.title,
      artist: recording.artist,
      durationMs: recording.durationSeconds ? recording.durationSeconds * 1000 : undefined,
    });
    await adjustArtistAffinity(DEFAULT_USER_ID, recording.artist, 0.6);
  }

  // Export best-effort vers Plex (§10 — jamais bloquant pour la lecture
  // locale, jamais depuis l'UI : uniquement ce endpoint serveur). N'agit que
  // si syncPolicy EXPORT_ONLY/BIDIRECTIONAL ET qu'un mapping Plex existe déjà
  // pour ce Recording (posé par un sync de bibliothèque précédent).
  if (body.type === "PLAY_COMPLETE") {
    exportPlexScrobble(recording.id).catch(() => {});
  }

  return NextResponse.json({ ...event, recording });
}
