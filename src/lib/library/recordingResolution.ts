import { createRecording, getRecording, type Recording } from "./recordingsStore";
import { addMapping, findRecordingIdByProvider } from "./providerMappingsStore";

export interface ExternalTrackInput {
  provider: string;
  providerTrackId: string;
  title: string;
  artist: string;
  album?: string;
  durationSeconds?: number;
  thumbnailUrl?: string;
}

/**
 * Résolution partagée (plan §67) — utilisée à la fois par "+ Bibliothèque" et
 * par l'enregistrement d'un événement de lecture : jamais deux chemins de
 * dédoublonnage différents pour la même règle (INTERDIT 8, jamais dupliquer
 * une identité qui vient de deux endroits du code).
 */
export function findOrCreateRecordingFromExternal(input: ExternalTrackInput): Recording {
  const existingId = findRecordingIdByProvider(input.provider, input.providerTrackId);
  if (existingId) {
    const existing = getRecording(existingId);
    if (existing) return existing;
  }

  const recording = createRecording({
    title: input.title,
    artist: input.artist,
    album: input.album,
    durationSeconds: input.durationSeconds,
    thumbnailUrl: input.thumbnailUrl,
  });
  addMapping({ entityType: "recording", entityId: recording.id, provider: input.provider, externalId: input.providerTrackId });
  return recording;
}
