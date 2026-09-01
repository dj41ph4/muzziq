import { getRecording } from "./recordingsStore";
import { listMappingsForRecording } from "./providerMappingsStore";
import { listMediaFiles } from "./mediaFilesStore";
import { resolveLocalMatch } from "@/lib/identity/resolver";

/**
 * Playback Resolver par Recording (plan §12) — le client demande "joue ce
 * recordingId", jamais un ID provider brut. Manquait jusqu'ici : les pages
 * qui affichent des Recordings (Accueil, Playlists) ne savaient pas les
 * jouer, faute de ce résolveur. Ordre de préférence (§11) : local d'abord,
 * puis le mapping provider existant.
 */
export interface ResolvedPlayback {
  kind: "local" | "provider";
  id: string;
}

export function resolveRecordingPlayback(recordingId: string): ResolvedPlayback | null {
  const recording = getRecording(recordingId);
  if (!recording) return null;

  const localMatch = resolveLocalMatch(
    { providerTrackId: "", provider: "", title: recording.title, artist: recording.artist, durationSeconds: recording.durationSeconds },
    listMediaFiles()
  );
  if (localMatch) return { kind: "local", id: localMatch.mediaFile.id };

  const providerMapping = listMappingsForRecording(recordingId).find((m) => m.provider === "youtube-music");
  if (providerMapping) return { kind: "provider", id: providerMapping.externalId };

  return null;
}
