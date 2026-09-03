/**
 * Contrats partagés entre providers, API et frontend (plan d'architecture §64).
 * Types "externes" — ce qu'un provider renvoie AVANT passage par l'IdentityResolver.
 * Ne jamais utiliser ces types comme identité canonique MuzziQ (INTERDIT 2).
 */

export interface ExternalTrack {
  /** ID interne au provider (ex. videoId YouTube) — jamais utilisé comme ID MuzziQ. */
  providerTrackId: string;
  provider: string;
  title: string;
  artist: string;
  artistProviderId?: string;
  album?: string;
  albumProviderId?: string;
  durationSeconds?: number;
  thumbnailUrl?: string;
  /** Code ISRC (identifiant international d'enregistrement), quand le provider l'expose — signal de matching quasi certain (plan §48/§104). */
  isrc?: string;
}

export interface ExternalArtist {
  providerArtistId: string;
  provider: string;
  name: string;
  thumbnailUrl?: string;
}

export interface ExternalAlbum {
  providerAlbumId: string;
  provider: string;
  title: string;
  artist: string;
  thumbnailUrl?: string;
}

export interface SearchQuery {
  text: string;
  /** Limite le type de résultat si le provider le supporte. */
  scope?: "songs" | "albums" | "artists" | "all";
}

export interface SearchResult {
  tracks: ExternalTrack[];
  albums: ExternalAlbum[];
  artists: ExternalArtist[];
}

export type ProviderProbeStatus = "OK" | "DEGRADED" | "BROKEN" | "AUTH_REQUIRED" | "RATE_LIMITED";

export interface ProviderHealthReport {
  provider: string;
  checkedAt: string;
  probes: Record<string, { status: ProviderProbeStatus; detail?: string }>;
}

export type PlaybackSourceType = "LOCAL" | "CACHE" | "PROVIDER";

export interface PlayableSource {
  type: PlaybackSourceType;
  url: string;
  expiresAt?: string;
  codec?: string;
  bitrate?: number;
}

/** Résultat honnête d'une tentative de résolution de lecture — jamais un flux fabriqué. */
export type PlaybackResolution =
  | { ok: true; source: PlayableSource }
  | { ok: false; status: ProviderProbeStatus; reason: string };

export interface MusicProvider {
  id: string;

  search(query: SearchQuery): Promise<SearchResult>;
  resolvePlayback(providerTrackId: string): Promise<PlaybackResolution>;
  health(): Promise<ProviderHealthReport>;
}
