import { withUserContextDb } from "./database";

/** Affinité par artiste (plan §43 UserTaste) — dimension "artist" uniquement pour l'instant, genre/mood viendront avec de vraies métadonnées MusicBrainz (pas avant, pas de dimension vide inventée). */
export interface ArtistAffinity {
  artist: string;
  affinity: number;
  confidence: number;
  evidenceCount: number;
  updatedAt: number;
}

function clampAffinity(v: number): number {
  return Math.max(-1, Math.min(1, Number.isFinite(v) ? v : 0));
}
function clampConfidence(v: number): number {
  return Math.max(0, Math.min(1, Number.isFinite(v) ? v : 0));
}

/**
 * Ajustement incrémental (pas un recalcul complet) — chaque signal déplace
 * l'affinité vers sa cible d'un pas proportionnel à sa force, la confiance
 * croît avec le nombre de preuves accumulées (jamais > 1). PLAY_COMPLETE et
 * LIKE poussent vers +1, SKIP/DISLIKE vers -1 — même logique de signal que
 * le plan §42 (repeat < 10m = affinité forte, skip < 20s = signal négatif).
 */
export async function adjustArtistAffinity(userId: string, artist: string, delta: number): Promise<boolean> {
  const key = artist.trim().toLowerCase();
  if (!key) return false;
  const now = Date.now();
  return withUserContextDb((db) => {
    const existing = db.prepare(`SELECT affinity, confidence, evidence_count FROM user_preferences WHERE user_id = ? AND dimension = 'artist' AND pref_key = ?`).get(userId, key) as
      | { affinity: number; confidence: number; evidence_count: number }
      | undefined;

    const prevAffinity = existing?.affinity ?? 0;
    const prevEvidence = existing?.evidence_count ?? 0;
    const step = 0.25; // vitesse d'apprentissage — assez rapide pour être visible en test réel, assez lent pour ne pas sur-réagir à un seul skip
    const nextAffinity = clampAffinity(prevAffinity + (delta - prevAffinity) * step);
    const nextEvidence = prevEvidence + 1;
    const nextConfidence = clampConfidence(nextEvidence / (nextEvidence + 3)); // s'approche de 1 asymptotiquement

    db.prepare(
      `INSERT INTO user_preferences(user_id, dimension, pref_key, label, affinity, confidence, source, evidence_count, updated_at)
       VALUES(?, 'artist', ?, ?, ?, ?, 'inferred', ?, ?)
       ON CONFLICT(user_id, dimension, pref_key) DO UPDATE SET
         label = excluded.label, affinity = excluded.affinity, confidence = excluded.confidence,
         evidence_count = excluded.evidence_count, updated_at = excluded.updated_at`
    ).run(userId, key, artist.trim(), nextAffinity, nextConfidence, nextEvidence, now);
    return true;
  }, false);
}

export async function getTopArtistAffinities(userId: string, limit = 10): Promise<ArtistAffinity[]> {
  const max = Math.max(1, Math.min(50, Math.round(limit || 1)));
  return withUserContextDb((db) => {
    const rows = db
      .prepare(
        `SELECT pref_key, label, affinity, confidence, evidence_count, updated_at
         FROM user_preferences WHERE user_id = ? AND dimension = 'artist' AND affinity > 0
         ORDER BY affinity * confidence DESC LIMIT ?`
      )
      .all(userId, max) as { label: string; affinity: number; confidence: number; evidence_count: number; updated_at: number }[];
    return rows.map((r) => ({
      artist: r.label,
      affinity: r.affinity,
      confidence: r.confidence,
      evidenceCount: r.evidence_count,
      updatedAt: r.updated_at,
    }));
  }, []);
}
