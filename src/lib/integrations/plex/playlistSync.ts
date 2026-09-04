import { getPlexConfig, updatePlexConfig } from "./store";
import { getAudioPlaylists, getPlaylistTracks } from "./client";
import { addLibraryItem } from "@/lib/library/libraryItemsStore";
import { findOrCreateRecordingFromExternal } from "@/lib/library/recordingResolution";
import { addMapping, findRecordingIdByProvider } from "@/lib/library/providerMappingsStore";
import { addPlaylistItem, createPlaylist, listPlaylistItems, listPlaylists } from "@/lib/library/playlistsStore";
import { bestMatch } from "@/lib/identity/resolver";
import { listRecordings } from "@/lib/library/recordingsStore";

const PROVIDER = "plex";
const PREFIX = "Plex · ";

export interface PlexPlaylistSyncSummary {
  ok: boolean;
  playlistsSeen: number;
  playlistsCreated: number;
  tracksSeen: number;
  tracksAdded: number;
  recordingsCreated: number;
  skipped: number;
  errors: string[];
}

/** Import idempotent Plex → MuzziQ. Plex reste strictement une source de
 * métadonnées : aucun flux, téléchargement ou contrôle de lecture n'est appelé. */
export async function syncPlexPlaylists(): Promise<PlexPlaylistSyncSummary> {
  const config = getPlexConfig();
  const summary: PlexPlaylistSyncSummary = { ok: false, playlistsSeen: 0, playlistsCreated: 0, tracksSeen: 0, tracksAdded: 0, recordingsCreated: 0, skipped: 0, errors: [] };
  if (config.syncPolicy !== "IMPORT_ONLY" && config.syncPolicy !== "BIDIRECTIONAL") {
    summary.errors.push("Politique Plex : IMPORT_ONLY ou BIDIRECTIONAL requis");
    return summary;
  }
  if (!config.serverUrl || !config.token) {
    summary.errors.push("Serveur Plex non connecté");
    return summary;
  }
  const recordings = listRecordings();
  const playlists = await getAudioPlaylists(config);
  const targets = new Map(listPlaylists().map((p) => [p.name.toLocaleLowerCase(), p]));
  for (const plexPlaylist of playlists) {
    summary.playlistsSeen++;
    const name = `${PREFIX}${plexPlaylist.title}`;
    const key = name.toLocaleLowerCase();
    const target = targets.get(key) ?? createPlaylist(name);
    if (!targets.has(key)) { targets.set(key, target); summary.playlistsCreated++; }
    const existingRecordingIds = new Set(listPlaylistItems(target.id).map((item) => item.recordingId));
    const tracks = await getPlaylistTracks(config, plexPlaylist.ratingKey);
    for (const track of tracks) {
      summary.tracksSeen++;
      try {
        let recordingId = findRecordingIdByProvider(PROVIDER, String(track.ratingKey));
        if (!recordingId) {
          const external = { title: track.title, artist: track.grandparentTitle ?? "Artiste inconnu", album: track.parentTitle, durationSeconds: track.duration ? track.duration / 1000 : undefined };
          const matched = bestMatch(external, recordings);
          const recording = matched?.candidate ?? findOrCreateRecordingFromExternal({ provider: PROVIDER, providerTrackId: String(track.ratingKey), ...external });
          if (!matched) { recordings.push(recording); summary.recordingsCreated++; }
          addMapping({ entityType: "recording", entityId: recording.id, provider: PROVIDER, externalId: String(track.ratingKey) });
          addLibraryItem(recording.id, "STREAM_ONLY");
          recordingId = recording.id;
        }
        if (!existingRecordingIds.has(recordingId)) {
          addPlaylistItem(target.id, recordingId);
          existingRecordingIds.add(recordingId);
          summary.tracksAdded++;
        }
      } catch (error) {
        summary.skipped++;
        summary.errors.push(`${track.title}: ${error instanceof Error ? error.message : String(error)}`);
      }
    }
  }
  summary.ok = summary.errors.length === 0;
  updatePlexConfig({ lastPlaylistSync: { at: Date.now(), ok: summary.ok, summary: `${summary.playlistsSeen} playlists Plex — ${summary.tracksAdded} titres ajoutés, ${summary.recordingsCreated} nouveaux enregistrements` } });
  return summary;
}
