/** Types Plex partagés entre client.ts, librarySync.ts, historySync.ts et l'UI. */

export interface PlexAccount {
  id: string;
  uuid: string;
  username: string;
  email: string;
  thumb: string | null;
  authToken: string;
}

export interface PlexServerConnection {
  uri: string;
  local: boolean;
  relay: boolean;
}

export interface PlexServerOption {
  name: string;
  machineIdentifier: string;
  owned: boolean;
  connections: PlexServerConnection[];
}

export interface PlexSection {
  key: string;
  title: string;
  type: string;
}

export interface PlexPathMapping {
  plexPrefix: string;
  localPrefix: string;
}

export interface PlexRawTrack {
  ratingKey: string;
  title: string;
  grandparentTitle?: string; // artiste
  parentTitle?: string; // album
  duration?: number; // ms
  updatedAt?: number; // unix seconds
  addedAt?: number;
  Media?: { Part?: { file?: string }[] }[];
}

/** Métadonnées d'une playlist Plex audio. Elles servent uniquement à importer
 * la liste de titres : aucun URI de lecture Plex n'est conservé ou exposé. */
export interface PlexAudioPlaylist {
  ratingKey: string;
  title: string;
  leafCount?: number;
  updatedAt?: number;
}
