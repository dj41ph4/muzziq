import { listRecentEvents, type PlaybackEvent } from "@/lib/history/playbackEventsStore";
import { listRecordings, type Recording } from "@/lib/library/recordingsStore";
import { listLibraryItems } from "@/lib/library/libraryItemsStore";
import { getTopArtistAffinities } from "@/lib/userContext/preferences";
import { DEFAULT_USER_ID } from "@/lib/userContext/types";

/**
 * DeterministicRecommendationEngine (plan §44) — pas de couche IA ici,
 * uniquement des règles explicites sur des données réelles (historique,
 * bibliothèque). La couche IA (§44 AIRecommendationLayer) reste hors scope
 * de cette session (nécessite une clé API que je n'ai pas) ; ce moteur
 * déterministe fonctionne indépendamment d'elle, comme prévu par le plan.
 */

export interface HomeRow {
  id: string;
  title: string;
  recordings: Recording[];
}

/** Dédoublonne une liste de recordings en conservant le premier ordre d'apparition. */
function dedupe(recordings: Recording[]): Recording[] {
  const seen = new Set<string>();
  return recordings.filter((r) => (seen.has(r.id) ? false : (seen.add(r.id), true)));
}

/** Continuer l'écoute (§46) — derniers morceaux joués, plus récent d'abord. */
export function getContinueListening(limit = 10): Recording[] {
  const events = listRecentEvents(200).filter((e: PlaybackEvent) => e.type === "PLAY_START");
  const recordings = listRecordings();
  const ordered = events.map((e) => recordings.find((r) => r.id === e.recordingId)).filter((r): r is Recording => !!r);
  return dedupe(ordered).slice(0, limit);
}

/** Albums récemment ajoutés (§46) — via LibraryItem.addedAt. */
export function getRecentlyAdded(limit = 10): Recording[] {
  const items = [...listLibraryItems()].sort((a, b) => b.addedAt.localeCompare(a.addedAt));
  const recordings = listRecordings();
  const ordered = items.map((i) => recordings.find((r) => r.id === i.recordingId)).filter((r): r is Recording => !!r);
  return dedupe(ordered).slice(0, limit);
}

/**
 * "Parce que vous aimez X" (§46) — règle simple et explicable : l'artiste le
 * plus écouté récemment, puis d'autres morceaux du même artiste déjà connus
 * de MuzziQ (catalogue) mais pas dans les derniers écoutés. Pas de similarité
 * inter-artiste ni de scoring de goût (§43 UserTaste) tant que le volume réel
 * de données ne le justifie pas — resterait un chiffre inventé sur un
 * historique quasi vide.
 */
export async function getBecauseYouLike(limit = 10): Promise<{ artist: string; recordings: Recording[] } | null> {
  const recordings = listRecordings();

  // Priorité au Context Engine SQL (plan §45, affinité pondérée par
  // confiance — un skip peut faire redescendre un artiste, pas juste
  // compter des lectures brutes) ; repli sur le comptage JSON si la DB
  // contexte est indisponible/désactivée (MUZZIQ_CONTEXT_ENGINE_DISABLED)
  // ou encore vide (tout juste démarré, aucune preuve accumulée).
  const topAffinities = await getTopArtistAffinities(DEFAULT_USER_ID, 1);
  let topArtist = topAffinities[0]?.artist;

  const events = listRecentEvents(200).filter((e) => e.type === "PLAY_START");
  const playedRecordings = events.map((e) => recordings.find((r) => r.id === e.recordingId)).filter((r): r is Recording => !!r);

  if (!topArtist) {
    if (playedRecordings.length === 0) return null;
    const artistCounts = new Map<string, number>();
    for (const r of playedRecordings) artistCounts.set(r.artist, (artistCounts.get(r.artist) ?? 0) + 1);
    topArtist = [...artistCounts.entries()].sort((a, b) => b[1] - a[1])[0][0];
  }

  const recentlyPlayedIds = new Set(playedRecordings.slice(0, 5).map((r) => r.id));
  const others = recordings.filter((r) => r.artist === topArtist && !recentlyPlayedIds.has(r.id));

  return { artist: topArtist, recordings: dedupe(others).slice(0, limit) };
}

export async function getHomeRows(): Promise<HomeRow[]> {
  const rows: HomeRow[] = [];

  const continueListening = getContinueListening();
  if (continueListening.length > 0) rows.push({ id: "continue", title: "Continuer l'écoute", recordings: continueListening });

  const recentlyAdded = getRecentlyAdded();
  if (recentlyAdded.length > 0) rows.push({ id: "recent", title: "Récemment ajoutés", recordings: recentlyAdded });

  const because = await getBecauseYouLike();
  if (because && because.recordings.length > 0) {
    rows.push({ id: "because", title: `Parce que vous aimez ${because.artist}`, recordings: because.recordings });
  }

  return rows;
}
