import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";
import { randomUUID } from "node:crypto";

/** Playlist / PlaylistItem (plan §6/§66). */

const PLAYLISTS_FILE = dataFile("playlists.json");
const ITEMS_FILE = dataFile("playlist-items.json");

export interface Playlist {
  id: string;
  name: string;
  createdAt: string;
}

export interface PlaylistItem {
  id: string;
  playlistId: string;
  recordingId: string;
  position: number;
  addedAt: string;
}

function loadPlaylists(): Playlist[] {
  return readJsonCached<Playlist[]>(PLAYLISTS_FILE, []);
}
function savePlaylists(items: Playlist[]): void {
  writeJsonCached(PLAYLISTS_FILE, items);
}
function loadItems(): PlaylistItem[] {
  return readJsonCached<PlaylistItem[]>(ITEMS_FILE, []);
}
function saveItems(items: PlaylistItem[]): void {
  writeJsonCached(ITEMS_FILE, items);
}

export function listPlaylists(): Playlist[] {
  return loadPlaylists();
}

export function getPlaylist(id: string): Playlist | undefined {
  return loadPlaylists().find((p) => p.id === id);
}

export function createPlaylist(name: string): Playlist {
  const playlist: Playlist = { id: randomUUID(), name, createdAt: new Date().toISOString() };
  const all = loadPlaylists();
  all.push(playlist);
  savePlaylists(all);
  return playlist;
}

export function deletePlaylist(id: string): boolean {
  const all = loadPlaylists();
  const next = all.filter((p) => p.id !== id);
  if (next.length === all.length) return false;
  savePlaylists(next);
  saveItems(loadItems().filter((i) => i.playlistId !== id));
  return true;
}

export function listPlaylistItems(playlistId: string): PlaylistItem[] {
  return loadItems()
    .filter((i) => i.playlistId === playlistId)
    .sort((a, b) => a.position - b.position);
}

export function addPlaylistItem(playlistId: string, recordingId: string): PlaylistItem {
  const items = loadItems();
  const existing = items.find((i) => i.playlistId === playlistId && i.recordingId === recordingId);
  if (existing) return existing;
  const position = items.filter((i) => i.playlistId === playlistId).length;
  const item: PlaylistItem = { id: randomUUID(), playlistId, recordingId, position, addedAt: new Date().toISOString() };
  items.push(item);
  saveItems(items);
  return item;
}

export function removePlaylistItem(itemId: string): boolean {
  const items = loadItems();
  const next = items.filter((i) => i.id !== itemId);
  if (next.length === items.length) return false;
  saveItems(next);
  return true;
}
