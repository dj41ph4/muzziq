/**
 * Indexer natif MuzziQ (plan §23) — parle Torznab, le protocole standard
 * exposé par la quasi-totalité des indexers torrent. Réimplémentation
 * indépendante inspirée du client Torznab de Movviz (protocole générique,
 * pas de logique cinéma portée — le scoring musical est déjà couvert par
 * `src/lib/acquisition/releaseScorer.ts`).
 */

export type IndexerAuthType = "apikey" | "credentials" | "x-api-key" | "none";

export interface IndexerCapabilities {
  search: boolean;
  categories: { id: number; name: string }[];
}

export interface ConfiguredIndexer {
  id: string;
  name: string;
  baseUrl: string;
  authType: IndexerAuthType;
  apiKey: string;
  username: string;
  password: string;
  categories: number[];
  enabled: boolean;
  priority: number;
  addedAt: number;
  lastTest?: { ok: boolean; at: number; detail: string };
  caps?: IndexerCapabilities | null;
}

/** Résultat de recherche normalisé, un candidat brut avant parsing/scoring musical. */
export interface IndexerRelease {
  guid: string;
  title: string;
  indexerId: string;
  indexer: string;
  size: number;
  seeders: number | null;
  leechers: number | null;
  publishDate: string | null;
  downloadUrl: string | null;
  magnetUrl: string | null;
  infoHash: string | null;
}
