import fs from "node:fs/promises";
import path from "node:path";
import { parseFile } from "music-metadata";
import { scanMusicDir } from "@/lib/library/scanner";

/**
 * Music Import Pipeline (plan §28) : un torrent terminé n'est pas encore un
 * album acquis. Lit les vraies balises de chaque fichier (jamais le nom de
 * fichier seul, §30), organise en `Artiste/Album (Année)/NN - Titre.ext`
 * (§32), copie la pochette si présente, puis relance le scanner pour que la
 * bibliothèque prenne le tout en compte (§34) — referme la boucle du plan
 * (téléchargement → import → LOCAL).
 *
 * Version actuelle : pas encore de détection multi-disque explicite, pas de
 * validation de qualité formelle contre un profil (§26/§27 — le scoring a
 * déjà filtré le candidat avant le grab), pas de gestion de doublon dédiée
 * (le scanner/IdentityResolver existants s'en chargent à la prochaine
 * recherche). Prochain incrément si un besoin réel apparaît, pas avant.
 */

const AUDIO_EXTENSIONS = new Set([".flac", ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".alac"]);
const ARTWORK_NAMES = new Set(["cover.jpg", "cover.png", "folder.jpg", "folder.png"]);

export interface ImportSummary {
  filesScanned: number;
  audioFilesImported: number;
  artworkCopied: number;
  failed: { file: string; reason: string }[];
  destinationDirs: string[];
}

async function listFilesRecursive(dir: string): Promise<string[]> {
  const out: string[] = [];
  let entries: import("node:fs").Dirent[];
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...(await listFilesRecursive(full)));
    else out.push(full);
  }
  return out;
}

function sanitizeSegment(name: string): string {
  return name.replace(/[/\\:*?"<>|]/g, "_").trim() || "Inconnu";
}

/**
 * Importe le contenu d'un dossier de téléchargement terminé vers la
 * bibliothèque musicale, puis relance un scan pour l'intégrer.
 */
export async function importMusicFromDownload(sourceDir: string, musicDir: string): Promise<ImportSummary> {
  const summary: ImportSummary = { filesScanned: 0, audioFilesImported: 0, artworkCopied: 0, failed: [], destinationDirs: [] };
  const files = await listFilesRecursive(sourceDir);
  summary.filesScanned = files.length;

  const destByAlbum = new Map<string, string>(); // clé "artist||album" -> dossier de destination

  for (const filePath of files) {
    const ext = path.extname(filePath).toLowerCase();
    if (!AUDIO_EXTENSIONS.has(ext)) continue;

    try {
      const meta = await parseFile(filePath);
      const artist = sanitizeSegment(meta.common.albumartist || meta.common.artist || "Artiste inconnu");
      const album = sanitizeSegment(meta.common.album || "Album inconnu");
      const year = meta.common.year ? ` (${meta.common.year})` : "";
      const trackNo = meta.common.track?.no ? String(meta.common.track.no).padStart(2, "0") + " - " : "";
      const title = sanitizeSegment(meta.common.title || path.basename(filePath, ext));

      const albumDir = path.join(musicDir, artist, `${album}${year}`);
      await fs.mkdir(albumDir, { recursive: true });

      const dest = await avoidCollision(path.join(albumDir, `${trackNo}${title}${ext}`));
      await moveFile(filePath, dest);

      destByAlbum.set(`${artist}||${album}`, albumDir);
      summary.audioFilesImported += 1;
    } catch (e) {
      summary.failed.push({ file: filePath, reason: e instanceof Error ? e.message : String(e) });
    }
  }

  // Pochette : copiée dans chaque dossier d'album créé pendant cet import,
  // si un fichier de couverture générique traîne à la racine du téléchargement.
  const artworkFile = files.find((f) => ARTWORK_NAMES.has(path.basename(f).toLowerCase()));
  if (artworkFile) {
    for (const albumDir of destByAlbum.values()) {
      try {
        await fs.copyFile(artworkFile, path.join(albumDir, path.basename(artworkFile)));
        summary.artworkCopied += 1;
      } catch {
        // pochette non critique — jamais bloquant
      }
    }
  }

  summary.destinationDirs = [...new Set(destByAlbum.values())];

  // Nettoyage du dossier de téléchargement source une fois les fichiers
  // audio effectivement déplacés (rmdir récursif — seulement des restes :
  // .nfo, .txt, playlists, dossiers vides).
  await fs.rm(sourceDir, { recursive: true, force: true }).catch(() => {});

  if (summary.audioFilesImported > 0) {
    await scanMusicDir(musicDir);
  }

  return summary;
}

async function moveFile(src: string, dest: string): Promise<void> {
  try {
    await fs.rename(src, dest);
  } catch {
    await fs.copyFile(src, dest);
    await fs.unlink(src).catch(() => {});
  }
}

async function avoidCollision(dest: string): Promise<string> {
  const ext = path.extname(dest);
  const base = dest.slice(0, -ext.length || undefined);
  let candidate = dest;
  let attempt = 2;
  while (
    await fs
      .access(candidate)
      .then(() => true)
      .catch(() => false)
  ) {
    candidate = `${base} (${attempt})${ext}`;
    attempt += 1;
  }
  return candidate;
}
