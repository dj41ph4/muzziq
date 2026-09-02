import { getPlexConfig, updatePlexConfig } from "./store";
import { getTrackHistory, scrobblePlexTrack } from "./client";
import { findRecordingIdByProvider, listMappingsForRecording } from "@/lib/library/providerMappingsStore";
import { recordEvent } from "@/lib/history/playbackEventsStore";

const PROVIDER = "plex";

export interface PlexHistorySyncSummary {
  ok: boolean;
  entriesSeen: number;
  imported: number;
  unresolved: number;
  errors: string[];
}

/**
 * Importe l'historique d'écoute Plex (pistes lues) vers le journal MuzziQ
 * (playback-events.json). Un événement Plex dont le ratingKey n'a pas encore
 * de Recording associé (pas de provider_mapping) est compté "unresolved" et
 * IGNORÉ — jamais de création à l'aveugle depuis un événement d'historique
 * seul (INTERDIT 7/8 : on ne fabrique pas d'identité à partir d'un simple
 * ratingKey non résolu par un sync de bibliothèque). Curseur incrémental
 * (lastHistoryWatermark) : ne relit jamais tout l'historique à chaque appel.
 */
export async function importPlexListenHistory(): Promise<PlexHistorySyncSummary> {
  const config = getPlexConfig();
  const summary: PlexHistorySyncSummary = { ok: false, entriesSeen: 0, imported: 0, unresolved: 0, errors: [] };

  if (config.syncPolicy !== "IMPORT_ONLY" && config.syncPolicy !== "BIDIRECTIONAL") {
    summary.errors.push("Politique de synchronisation actuelle n'inclut pas l'import");
    return summary;
  }
  if (!config.serverUrl || !config.token) {
    summary.errors.push("Serveur Plex non connecté");
    return summary;
  }

  let entries;
  try {
    entries = await getTrackHistory(config, { sinceUnixSeconds: config.lastHistoryWatermark });
  } catch (e) {
    summary.errors.push(e instanceof Error ? e.message : String(e));
    return summary;
  }

  summary.entriesSeen = entries.length;
  let newWatermark = config.lastHistoryWatermark ?? 0;

  for (const entry of entries) {
    const recordingId = findRecordingIdByProvider(PROVIDER, entry.ratingKey);
    if (!recordingId) {
      summary.unresolved += 1;
      continue;
    }
    recordEvent({ recordingId, type: "PLAY_COMPLETE", source: "PROVIDER" });
    summary.imported += 1;
    if (entry.viewedAt > newWatermark) newWatermark = entry.viewedAt;
  }

  summary.ok = true;
  const text = `${summary.entriesSeen} événements vus — ${summary.imported} importés, ${summary.unresolved} sans morceau lié (synchronise la bibliothèque d'abord)`;
  updatePlexConfig({
    lastHistoryWatermark: newWatermark || config.lastHistoryWatermark,
    lastHistorySync: { at: Date.now(), ok: true, summary: text },
  });
  return summary;
}

/**
 * Export best-effort d'une lecture MuzziQ vers Plex (scrobble) — appelé
 * depuis POST /api/events sur PLAY_COMPLETE. Ne fait jamais échouer la
 * lecture locale : toute erreur est avalée par l'appelant. Ne fait rien si
 * aucun mapping Plex n'existe pour ce Recording (pas de piste Plex connue).
 */
export async function exportPlexScrobble(recordingId: string): Promise<boolean> {
  const config = getPlexConfig();
  if (config.syncPolicy !== "EXPORT_ONLY" && config.syncPolicy !== "BIDIRECTIONAL") return false;
  if (!config.serverUrl || !config.token) return false;

  const mapping = listMappingsForRecording(recordingId).find((m) => m.provider === PROVIDER);
  if (!mapping) return false;

  return scrobblePlexTrack(config, mapping.externalId);
}
