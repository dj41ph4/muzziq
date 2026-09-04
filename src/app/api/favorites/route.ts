import { NextResponse } from "next/server";
import { findOrCreateRecordingFromExternal } from "@/lib/library/recordingResolution";
import { listRecordings } from "@/lib/library/recordingsStore";
import { listMappingsForRecording } from "@/lib/library/providerMappingsStore";
import { addFavorite, listFavorites, removeFavorite } from "@/lib/library/favoritesStore";

export const dynamic = "force-dynamic";

export async function GET() {
  const recordings = new Map(listRecordings().map((recording) => [recording.id, recording]));
  const favorites = listFavorites().map((favorite) => {
    const mapping = listMappingsForRecording(favorite.recordingId).find((item) => item.provider === "youtube-music" || item.provider === "spotify");
    return {
      ...favorite,
      recording: recordings.get(favorite.recordingId),
      provider: mapping?.provider,
      providerTrackId: mapping?.externalId,
    };
  });
  return NextResponse.json({ favorites });
}

interface FavoriteBody {
  recordingId?: string;
  provider?: string;
  providerTrackId?: string;
  title?: string;
  artist?: string;
  album?: string;
  durationSeconds?: number;
  thumbnailUrl?: string;
}

export async function POST(req: Request) {
  const body = (await req.json().catch(() => ({}))) as FavoriteBody;
  let recordingId = body.recordingId;
  if (!recordingId) {
    if (!body.provider || !body.providerTrackId || !body.title || !body.artist) {
      return NextResponse.json({ error: "recordingId ou métadonnées du titre requises" }, { status: 400 });
    }
    recordingId = findOrCreateRecordingFromExternal({
      provider: body.provider,
      providerTrackId: body.providerTrackId,
      title: body.title,
      artist: body.artist,
      album: body.album,
      durationSeconds: body.durationSeconds,
      thumbnailUrl: body.thumbnailUrl,
    }).id;
  }

  return NextResponse.json(addFavorite(recordingId));
}

export async function DELETE(req: Request) {
  const recordingId = new URL(req.url).searchParams.get("recordingId");
  if (!recordingId) return NextResponse.json({ error: "recordingId requis" }, { status: 400 });
  removeFavorite(recordingId);
  return NextResponse.json({ ok: true });
}
