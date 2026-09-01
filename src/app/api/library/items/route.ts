import { NextResponse } from "next/server";
import { listRecordings } from "@/lib/library/recordingsStore";
import { findOrCreateRecordingFromExternal } from "@/lib/library/recordingResolution";
import { addLibraryItem, listLibraryItems, type AddPolicy } from "@/lib/library/libraryItemsStore";

export const dynamic = "force-dynamic";

interface AddBody {
  provider: string;
  providerTrackId: string;
  title: string;
  artist: string;
  album?: string;
  durationSeconds?: number;
  thumbnailUrl?: string;
  addPolicy?: AddPolicy;
}

export async function GET() {
  const items = listLibraryItems();
  const recordings = listRecordings();
  const enriched = items.map((item) => ({
    ...item,
    recording: recordings.find((r) => r.id === item.recordingId),
  }));
  return NextResponse.json({ items: enriched });
}

/**
 * "+ Bibliothèque" (plan §48) — dédupe via provider_mappings avant de créer
 * un nouveau Recording, pour ne jamais dupliquer la même identité parce
 * qu'elle est ajoutée deux fois depuis la même source (INTERDIT 8).
 */
export async function POST(req: Request) {
  const body: AddBody = await req.json();
  if (!body.provider || !body.providerTrackId || !body.title || !body.artist) {
    return NextResponse.json({ error: "provider, providerTrackId, title, artist requis" }, { status: 400 });
  }

  const recording = findOrCreateRecordingFromExternal(body);
  const item = addLibraryItem(recording.id, body.addPolicy ?? "STREAM_ONLY");
  return NextResponse.json({ ...item, recording });
}
