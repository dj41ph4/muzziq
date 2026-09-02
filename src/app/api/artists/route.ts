import { NextResponse } from "next/server";
import { listMediaFiles } from "@/lib/library/mediaFilesStore";

export const dynamic = "force-dynamic";

/**
 * Regroupement des fichiers locaux par artiste (plan Android §13 —
 * browse artiste). Dérivé de la bibliothèque locale scannée, seule source
 * de catalogue "parcourable" fiable aujourd'hui côté serveur (le catalogue
 * YouTube Music n'a qu'une recherche, pas de browse par artiste — voir
 * docs/reverse-engineering/youtube-music). Aucune agrégation inventée : un
 * artiste n'apparaît que s'il a au moins un fichier réellement scanné.
 */
export async function GET() {
  const files = listMediaFiles();
  const byArtist = new Map<string, { name: string; trackCount: number; albums: Set<string> }>();

  for (const f of files) {
    const key = f.artist.trim().toLowerCase();
    if (!key) continue;
    const entry = byArtist.get(key) ?? { name: f.artist, trackCount: 0, albums: new Set<string>() };
    entry.trackCount += 1;
    if (f.album) entry.albums.add(f.album);
    byArtist.set(key, entry);
  }

  const artists = Array.from(byArtist.entries())
    .map(([id, v]) => ({ id, name: v.name, trackCount: v.trackCount, albumCount: v.albums.size }))
    .sort((a, b) => a.name.localeCompare(b.name));

  return NextResponse.json({ artists });
}
