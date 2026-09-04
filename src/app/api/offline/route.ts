import { NextResponse } from "next/server";
import { getRecording } from "@/lib/library/recordingsStore";
import { findOrCreateRecordingFromExternal } from "@/lib/library/recordingResolution";
import { resolveRecordingPlayback } from "@/lib/library/recordingPlayback";
import { getMediaFile } from "@/lib/library/mediaFilesStore";
import { resolveYoutubeMusicPlayback } from "@/providers/youtube-music/playbackResolver";
import {
  createOfflineDownload,
  findOfflineDownloadByRecording,
  listOfflineDownloads,
  updateOfflineDownload,
  type OfflineDownload,
} from "@/lib/library/offlineDownloadsStore";
import { dataFile } from "@/lib/config";
import fs from "node:fs";
import path from "node:path";

export const dynamic = "force-dynamic";

const OFFLINE_DIR = dataFile("offline");

/** Défense en profondeur (INTERDIT 6 du plan) — recordingId vient de getRecording() donc déjà connu, mais jamais fait confiance aveuglément comme segment de chemin. */
function sanitizeSegment(name: string): string {
  return name.replace(/[/\\:]/g, "_").replace(/\.\.+/g, "_");
}

export async function GET() {
  const downloads = listOfflineDownloads();
  return NextResponse.json({ downloads });
}

interface OfflineRequestBody {
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
  const body: OfflineRequestBody = await req.json().catch(() => ({}));
  if (!body.recordingId) {
    return NextResponse.json({ error: "recordingId requis" }, { status: 400 });
  }

  const recording = body.recordingId
    ? getRecording(body.recordingId)
    : body.provider && body.providerTrackId && body.title && body.artist
      ? findOrCreateRecordingFromExternal({
          provider: body.provider,
          providerTrackId: body.providerTrackId,
          title: body.title,
          artist: body.artist,
          album: body.album,
          durationSeconds: body.durationSeconds,
          thumbnailUrl: body.thumbnailUrl,
        })
      : undefined;
  if (!recording) {
    return NextResponse.json({ error: "Recording introuvable" }, { status: 404 });
  }

  // Idempotent : un téléchargement déjà en cours ou terminé pour ce
  // recordingId n'est jamais relancé en double — évite un deuxième fetch
  // concurrent du même flux.
  const existing = findOfflineDownloadByRecording(recording.id);
  if (existing && existing.state !== "FAILED") {
    return NextResponse.json(existing);
  }

  const entry = existing ?? createOfflineDownload({ recordingId: recording.id, title: recording.title, artist: recording.artist, album: recording.album });
  if (existing) {
    updateOfflineDownload(entry.id, { state: "QUEUED", error: null });
  }

  const resolved = resolveRecordingPlayback(recording.id);
  if (!resolved) {
    const failed = updateOfflineDownload(entry.id, { state: "FAILED", error: "Aucune source disponible pour ce morceau" });
    return NextResponse.json(failed, { status: 502 });
  }

  if (resolved.kind === "local") {
    const mediaFile = getMediaFile(resolved.id);
    if (!mediaFile) {
      const failed = updateOfflineDownload(entry.id, { state: "FAILED", error: "Fichier local introuvable" });
      return NextResponse.json(failed, { status: 404 });
    }
    // Déjà "hors ligne" par définition — rien à télécharger, juste marquer
    // l'entrée COMPLETED en pointant vers le fichier déjà présent sur disque.
    const completed = updateOfflineDownload(entry.id, {
      state: "COMPLETED",
      sourceKind: "local",
      filePath: mediaFile.path,
      sizeBytes: mediaFile.sizeBytes,
      error: null,
    });
    return NextResponse.json(completed);
  }

  if (resolved.kind === "offline") {
    // Déjà téléchargé précédemment (findOfflineDownloadByRecording aurait dû
    // court-circuiter avant, mais rester honnête si cet état est atteint).
    const existingOffline = findOfflineDownloadByRecording(recording.id);
    return NextResponse.json(existingOffline ?? entry);
  }

  // resolved.kind === "provider" — vrai téléchargement réseau requis.
  updateOfflineDownload(entry.id, { state: "DOWNLOADING", sourceKind: "provider" });

  try {
    const playback = await resolveYoutubeMusicPlayback(resolved.id);
    if (!playback.ok) {
      const failed = updateOfflineDownload(entry.id, { state: "FAILED", error: `${playback.reason} (${playback.status})` });
      return NextResponse.json(failed, { status: 502 });
    }

    // Timeout explicite : sans lui, un flux qui ne répond jamais (CDN qui
    // n'accepte pas la connexion, réseau qui coupe en silence) laisse la
    // requête POST pendante indéfiniment au lieu de retomber sur un FAILED
    // visible — jamais d'attente infinie sur un flux externe.
    let streamRes: Response;
    try {
      streamRes = await fetch(playback.source.url, { signal: AbortSignal.timeout(120_000) });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      const failed = updateOfflineDownload(entry.id, { state: "FAILED", error: `Flux inaccessible : ${message}` });
      return NextResponse.json(failed, { status: 502 });
    }
    if (!streamRes.ok || !streamRes.body) {
      const failed = updateOfflineDownload(entry.id, { state: "FAILED", error: `Flux inaccessible (HTTP ${streamRes.status})` });
      return NextResponse.json(failed, { status: 502 });
    }

    const ext = extFromCodec(playback.source.codec) ?? "m4a";
    const fileName = `${sanitizeSegment(recording.id)}.${sanitizeSegment(ext)}`;
    const filePath = path.join(OFFLINE_DIR, fileName);

    // Sécurité fichier (INTERDIT 6) : le chemin final doit rester sous
    // OFFLINE_DIR — jamais faire confiance à un nom construit sans vérifier
    // qu'il ne s'en échappe pas, même si sanitizeSegment le rend improbable ici.
    if (!path.resolve(filePath).startsWith(path.resolve(OFFLINE_DIR) + path.sep)) {
      const failed = updateOfflineDownload(entry.id, { state: "FAILED", error: "Chemin de destination invalide" });
      return NextResponse.json(failed, { status: 500 });
    }

    await fs.promises.mkdir(OFFLINE_DIR, { recursive: true });
    const buffer = Buffer.from(await streamRes.arrayBuffer());
    await fs.promises.writeFile(filePath, buffer);
    const stat = await fs.promises.stat(filePath);

    const completed = updateOfflineDownload(entry.id, {
      state: "COMPLETED",
      sourceKind: "provider",
      filePath,
      sizeBytes: stat.size,
      error: null,
    });
    return NextResponse.json(completed);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    const failed = updateOfflineDownload(entry.id, { state: "FAILED", error: message });
    return NextResponse.json(failed satisfies OfflineDownload | undefined, { status: 502 });
  }
}

function extFromCodec(codec?: string): string | null {
  if (!codec) return null;
  if (codec.includes("mp4a") || codec.includes("audio/mp4")) return "m4a";
  if (codec.includes("opus")) return "opus";
  if (codec.includes("webm")) return "webm";
  if (codec.includes("mpeg") || codec.includes("mp3")) return "mp3";
  return null;
}
