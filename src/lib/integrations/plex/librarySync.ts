import { getPlexConfig, updatePlexConfig } from "./store";
import { getSectionTracks } from "./client";
import { applyPathMapping } from "./pathMapping";
import type { PlexRawTrack } from "./types";
import { listMediaFiles } from "@/lib/library/mediaFilesStore";
import { listRecordings } from "@/lib/library/recordingsStore";
import { findRecordingIdByProvider, addMapping } from "@/lib/library/providerMappingsStore";
import { findOrCreateRecordingFromExternal } from "@/lib/library/recordingResolution";
import { addLibraryItem } from "@/lib/library/libraryItemsStore";
import { bestMatch } from "@/lib/identity/resolver";

const PROVIDER = "plex";

export interface PlexLibrarySyncSummary {
  ok: boolean;
  sectionsScanned: number;
  tracksSeen: number;
  alreadyLinked: number;
  pathMatched: number;
  matchedExisting: number;
  created: number;
  skipped: number;
  errors: string[];
}

function trackFilePath(track: PlexRawTrack): string | null {
  return track.Media?.[0]?.Part?.[0]?.file ?? null;
}

/**
 * Synchronise les sections musicales Plex sélectionnées vers le catalogue
 * MuzziQ (plan §2/§104). Jamais de fusion sous le seuil de confiance
 * (INTERDIT 7) : un morceau Plex non reconnu avec certitude devient un
 * NOUVEAU Recording (sûr — pas de fusion) plutôt que d'être forcé dans un
 * Recording existant. Aucune suppression (INTERDIT 8) : cette fonction
 * n'importe/ne lie que dans le sens Plex → MuzziQ, jamais l'inverse ici.
 */
export async function syncPlexMusicLibrary(): Promise<PlexLibrarySyncSummary> {
  const config = getPlexConfig();
  const summary: PlexLibrarySyncSummary = {
    ok: false,
    sectionsScanned: 0,
    tracksSeen: 0,
    alreadyLinked: 0,
    pathMatched: 0,
    matchedExisting: 0,
    created: 0,
    skipped: 0,
    errors: [],
  };

  if (config.syncPolicy !== "IMPORT_ONLY" && config.syncPolicy !== "BIDIRECTIONAL") {
    summary.errors.push("Politique de synchronisation actuelle n'inclut pas l'import (IMPORT_ONLY ou BIDIRECTIONAL requis)");
    return summary;
  }
  if (!config.serverUrl || !config.token) {
    summary.errors.push("Serveur Plex non connecté");
    return summary;
  }
  if (config.musicSections.length === 0) {
    summary.errors.push("Aucune bibliothèque musicale Plex sélectionnée");
    return summary;
  }

  const localFiles = listMediaFiles();
  const localByPath = new Map(localFiles.map((f) => [f.path.toLowerCase(), f]));
  const existingRecordings = listRecordings();

  for (const section of config.musicSections) {
    let tracks: PlexRawTrack[];
    try {
      tracks = await getSectionTracks(config, section.key);
    } catch (e) {
      summary.errors.push(`Section "${section.title}": ${e instanceof Error ? e.message : String(e)}`);
      continue;
    }
    summary.sectionsScanned += 1;

    for (const track of tracks) {
      summary.tracksSeen += 1;
      try {
        // Déjà lié lors d'un sync précédent : rien à refaire.
        if (findRecordingIdByProvider(PROVIDER, track.ratingKey)) {
          summary.alreadyLinked += 1;
          continue;
        }

        const artist = track.grandparentTitle ?? "Artiste inconnu";
        const title = track.title;
        const album = track.parentTitle;
        const durationSeconds = track.duration ? track.duration / 1000 : undefined;

        // Signal le plus fort : le chemin Plex, une fois remappé, correspond
        // exactement à un fichier déjà scanné localement — on utilise alors
        // les tags LOCAUX (plus fiables, c'est le fichier qui fait foi) pour
        // la comparaison/le nouveau Recording, jamais ceux de Plex.
        const rawPath = trackFilePath(track);
        const localPath = rawPath ? applyPathMapping(rawPath, config.pathMappings) : null;
        const localMatch = localPath ? localByPath.get(localPath.toLowerCase()) : undefined;
        const external = localMatch
          ? { title: localMatch.title, artist: localMatch.artist, album: localMatch.album, durationSeconds: localMatch.durationSeconds }
          : { title, artist, album, durationSeconds };
        if (localMatch) summary.pathMatched += 1;

        const match = bestMatch(external, existingRecordings);
        if (match) {
          addMapping({ entityType: "recording", entityId: match.candidate.id, provider: PROVIDER, externalId: track.ratingKey });
          summary.matchedExisting += 1;
          continue;
        }

        const recording = findOrCreateRecordingFromExternal({
          provider: PROVIDER,
          providerTrackId: track.ratingKey,
          title: external.title,
          artist: external.artist,
          album: external.album,
          durationSeconds: external.durationSeconds,
        });
        addLibraryItem(recording.id, "STREAM_ONLY");
        existingRecordings.push(recording);
        summary.created += 1;
      } catch (e) {
        summary.skipped += 1;
        summary.errors.push(`"${track.title}" (${track.ratingKey}): ${e instanceof Error ? e.message : String(e)}`);
      }
    }
  }

  summary.ok = summary.errors.length === 0 || summary.tracksSeen > 0;
  const text = `${summary.tracksSeen} pistes vues — ${summary.created} créées, ${summary.matchedExisting} liées à un morceau existant, ${summary.pathMatched} via correspondance de chemin, ${summary.alreadyLinked} déjà synchronisées, ${summary.skipped} échecs`;
  updatePlexConfig({ lastLibrarySync: { at: Date.now(), ok: summary.ok, summary: text } });
  return summary;
}
