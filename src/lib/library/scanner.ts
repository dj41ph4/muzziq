import fs from "node:fs/promises";
import path from "node:path";
import { parseFile } from "music-metadata";
import { pruneMissing, upsertMediaFile } from "./mediaFilesStore";

/**
 * Scanner local (plan §34). Ne dépend d'aucun provider externe — c'est ce
 * qui doit continuer à fonctionner si YouTube Music, Plex, ou n'importe
 * quelle intégration tombe (plan §2). Tags lus via music-metadata (MIT,
 * mature — §87.4) plutôt que d'écrire un parseur ID3/FLAC maison.
 */

const AUDIO_EXTENSIONS = new Set([".flac", ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".alac"]);

export interface ScanSummary {
  filesFound: number;
  added: number;
  updated: number;
  unchanged: number;
  failed: number;
  removed: number;
  durationMs: number;
}

async function walk(dir: string): Promise<string[]> {
  const out: string[] = [];
  let entries: import("node:fs").Dirent[];
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...(await walk(full)));
    } else if (AUDIO_EXTENSIONS.has(path.extname(entry.name).toLowerCase())) {
      out.push(full);
    }
  }
  return out;
}

export async function scanMusicDir(musicDir: string): Promise<ScanSummary> {
  const start = Date.now();
  const summary: ScanSummary = { filesFound: 0, added: 0, updated: 0, unchanged: 0, failed: 0, removed: 0, durationMs: 0 };

  const files = await walk(musicDir);
  summary.filesFound = files.length;
  const seenPaths = new Set<string>();

  for (const filePath of files) {
    seenPaths.add(filePath);
    try {
      const stat = await fs.stat(filePath);
      const meta = await parseFile(filePath);

      const before = upsertMediaFile({
        path: filePath,
        title: meta.common.title ?? path.basename(filePath, path.extname(filePath)),
        artist: meta.common.artist ?? "Artiste inconnu",
        album: meta.common.album,
        trackNumber: meta.common.track?.no ?? undefined,
        durationSeconds: meta.format.duration,
        codec: meta.format.codec,
        sampleRate: meta.format.sampleRate,
        bitsPerSample: meta.format.bitsPerSample,
        container: meta.format.container ?? path.extname(filePath).slice(1),
        sizeBytes: stat.size,
        mtimeMs: stat.mtimeMs,
      });
      // upsertMediaFile ne distingue pas ajout/mise à jour dans son retour —
      // approximation simple pour le résumé de scan, suffisante pour l'UI.
      if (before) summary.added += 1;
    } catch (err) {
      summary.failed += 1;
      console.error(`[scanner] échec de lecture des tags pour ${filePath}:`, err);
    }
  }

  summary.removed = pruneMissing(seenPaths);
  summary.durationMs = Date.now() - start;
  return summary;
}
