import { getRecording } from "./recordingsStore";
import { listMappingsForRecording } from "./providerMappingsStore";
import { listMediaFiles } from "./mediaFilesStore";
import { resolveLocalMatch } from "@/lib/identity/resolver";
import { findCompletedOfflineDownload } from "./offlineDownloadsStore";

/**
 * Playback Resolver par Recording (plan §12) — le client demande "joue ce
 * recordingId", jamais un ID provider brut. Manquait jusqu'ici : les pages
 * qui affichent des Recordings (Accueil, Playlists) ne savaient pas les
 * jouer, faute de ce résolveur. Ordre de préférence (§11) : local d'abord,
 * puis un téléchargement hors ligne déjà terminé (évite de re-résoudre le
 * flux provider — souvent expirant — à chaque lecture), puis le mapping
 * provider existant.
 */
export interface ResolvedPlayback {
  kind: "local" | "offline" | "provider";
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

  // Téléchargement hors ligne réellement terminé (fichier écrit sous
  // DATA_DIR/offline) avant de retenter une résolution réseau — sinon chaque
  // lecture re-déclencherait une nouvelle résolution de flux provider,
  // inutile une fois le fichier déjà présent sur disque.
  const offline = findCompletedOfflineDownload(recordingId);
  if (offline?.sourceKind === "provider" && offline.filePath) {
    return { kind: "offline", id: offline.id };
  }

  const providerMapping = listMappingsForRecording(recordingId).find((m) => m.provider === "youtube-music");
  if (providerMapping) return { kind: "provider", id: providerMapping.externalId };

  return null;
}
