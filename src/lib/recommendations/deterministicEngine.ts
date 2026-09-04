import { listRecentEvents, type PlaybackEvent } from "@/lib/history/playbackEventsStore";
import { listRecordings, type Recording } from "@/lib/library/recordingsStore";
import { listFavorites } from "@/lib/library/favoritesStore";
import { listLibraryItems } from "@/lib/library/libraryItemsStore";
import { getTopArtistAffinities } from "@/lib/userContext/preferences";
import { DEFAULT_USER_ID } from "@/lib/userContext/types";
import { getSettings } from "@/lib/settings/store";
import { findOrCreateRecordingFromExternal } from "@/lib/library/recordingResolution";
import { youtubeMusicProvider } from "@/providers/youtube-music";

/**
 * Moteur de recommandation déterministe : aucune playlist statique et aucun
 * titre inventé. Les candidats viennent du provider, puis sont classés à
 * partir des signaux réellement enregistrés par MuzziQ (écoute, completion,
 * skip et favoris). Le résultat est explicable et testable sans modèle IA.
 */

export interface HomeRow {
  id: string;
  title: string;
  recordings: Recording[];
}

interface ArtistSignal {
  artist: string;
  score: number;
}

let trendsCache: { expiresAt: number; rows: HomeRow[] } | null = null;

function dedupe(recordings: Recording[]): Recording[] {
  const seen = new Set<string>();
  return recordings.filter((r) => (seen.has(r.id) ? false : (seen.add(r.id), true)));
}

function recentEvents(): PlaybackEvent[] {
  return listRecentEvents(500);
}

function withoutHiddenLocalFiles(recordings: Recording[]): Recording[] {
  if (getSettings().showLocalFiles) return recordings;
  const localIds = new Set(recentEvents().filter((event) => event.source === "LOCAL").map((event) => event.recordingId));
  return recordings.filter((recording) => !localIds.has(recording.id));
}

/** Derniers morceaux réellement démarrés, dédupliqués et filtrés par préférence locale. */
export function getContinueListening(limit = 10): Recording[] {
  const events = recentEvents().filter((event) => event.type === "PLAY_START");
  const recordings = new Map(listRecordings().map((recording) => [recording.id, recording]));
  return withoutHiddenLocalFiles(dedupe(events.map((event) => recordings.get(event.recordingId)).filter((r): r is Recording => !!r))).slice(0, limit);
}

/** Conservé pour les écrans de bibliothèque, mais volontairement absent de l'accueil. */
export function getRecentlyAdded(limit = 10): Recording[] {
  const recordings = new Map(listRecordings().map((recording) => [recording.id, recording]));
  return listLibraryItems()
    .sort((a, b) => b.addedAt.localeCompare(a.addedAt))
    .map((item) => recordings.get(item.recordingId))
    .filter((r): r is Recording => !!r)
    .filter((recording, index, all) => all.findIndex((item) => item.id === recording.id) === index)
    .slice(0, limit);
}

function getLocalArtistSignals(): ArtistSignal[] {
  const events = recentEvents();
  const recordings = new Map(listRecordings().map((recording) => [recording.id, recording]));
  const scores = new Map<string, ArtistSignal>();
  const now = Date.now();

  for (const event of events) {
    const recording = recordings.get(event.recordingId);
    if (!recording) continue;
    const ageDays = Math.max(0, (now - Date.parse(event.at)) / 86_400_000);
    const recency = Math.exp(-ageDays / 21);
    const signal = event.type === "PLAY_COMPLETE" ? 4 : event.type === "SKIP" ? -2.5 : 1;
    const key = recording.artist.trim().toLowerCase();
    const current = scores.get(key) ?? { artist: recording.artist, score: 0 };
    current.score += signal * recency;
    scores.set(key, current);
  }

  for (const favorite of listFavorites()) {
    const recording = recordings.get(favorite.recordingId);
    if (!recording) continue;
    const key = recording.artist.trim().toLowerCase();
    const current = scores.get(key) ?? { artist: recording.artist, score: 0 };
    current.score += 3;
    scores.set(key, current);
  }

  return [...scores.values()].filter((signal) => signal.score > 0).sort((a, b) => b.score - a.score).slice(0, 5);
}

async function getArtistSignals(): Promise<ArtistSignal[]> {
  const merged = new Map<string, ArtistSignal>();
  for (const signal of getLocalArtistSignals()) merged.set(signal.artist.toLowerCase(), signal);

  const affinities = await getTopArtistAffinities(DEFAULT_USER_ID, 12);
  for (const affinity of affinities) {
    const key = affinity.artist.toLowerCase();
    const current = merged.get(key);
    merged.set(key, {
      artist: affinity.artist,
      score: (current?.score ?? 0) + affinity.affinity * (1 + affinity.confidence) * 5,
    });
  }

  return [...merged.values()].filter((signal) => signal.score > 0).sort((a, b) => b.score - a.score).slice(0, 5);
}

async function searchShelf(query: string, id: string, title: string, limit = 10): Promise<HomeRow | null> {
  try {
    const result = await youtubeMusicProvider.search({ text: query, scope: "songs" });
    const recordings = dedupe(result.tracks.map((track) => findOrCreateRecordingFromExternal(track))).slice(0, limit);
    return recordings.length > 0 ? { id, title, recordings } : null;
  } catch {
    return null;
  }
}

async function getForYou(limit = 12): Promise<HomeRow | null> {
  const signals = await getArtistSignals();
  if (signals.length === 0) return null;

  const playedIds = new Set(recentEvents().filter((event) => event.type === "PLAY_START").map((event) => event.recordingId));
  const candidates = new Map<string, { recording: Recording; score: number }>();

  // On interroge plusieurs artistes réellement appréciés, puis on fusionne
  // les résultats en conservant un score de préférence par candidat.
  for (const signal of signals) {
    try {
      const result = await youtubeMusicProvider.search({ text: signal.artist, scope: "songs" });
      for (const track of result.tracks) {
        const recording = findOrCreateRecordingFromExternal(track);
        if (playedIds.has(recording.id)) continue;
        const existing = candidates.get(recording.id);
        candidates.set(recording.id, { recording, score: (existing?.score ?? 0) + signal.score });
      }
    } catch {
      // Une source indisponible ne doit pas effacer les autres signaux valides.
    }
  }

  const ranked = withoutHiddenLocalFiles([...candidates.values()].sort((a, b) => b.score - a.score).map((candidate) => candidate.recording));
  return ranked.length > 0 ? { id: "for-you", title: "Pour toi", recordings: ranked.slice(0, limit) } : null;
}

export async function getHomeRows(): Promise<HomeRow[]> {
  const rows: HomeRow[] = [];
  const continueListening = getContinueListening();
  if (continueListening.length > 0) rows.push({ id: "continue", title: "Continuer l'écoute", recordings: continueListening });

  const forYou = await getForYou();
  if (forYou) rows.push(forYou);

  // Cette étagère est volontairement distincte de "Pour toi" : elle vient
  // d'une recherche provider en direct, donc ce sont des tendances externes,
  // pas une fausse liste locale présentée comme personnalisée.
  if (!trendsCache || trendsCache.expiresAt < Date.now()) {
    const year = new Date().getFullYear();
    const [trending, newReleases] = await Promise.all([
      searchShelf(`Top hits ${year}`, "trending", "Hits du moment"),
      searchShelf(`Nouveautés musique ${year}`, "new-releases", "Nouveautés à découvrir"),
    ]);
    trendsCache = { expiresAt: Date.now() + 10 * 60_000, rows: [trending, newReleases].filter((row): row is HomeRow => row !== null) };
  }
  rows.push(...trendsCache.rows);
  return rows;
}
