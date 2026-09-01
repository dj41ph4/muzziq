import path from "node:path";
import fsp from "node:fs/promises";
import { DOWNLOAD_COMPLETE_DIR } from "./webTorrentBackend";
import { getSettings } from "@/lib/settings/store";
import { importMusicFromDownload } from "@/lib/acquisition/musicImportPipeline";

/**
 * Étape d'import (plan §59 + §28) : un torrent terminé n'est jamais laissé
 * dans le dossier de téléchargement incomplet — déplacé vers un dossier de
 * staging (`downloads/complete`) une fois WebTorrent lui-même confirmé
 * "done" (hash vérifié), jamais écrit directement dans le dossier musique.
 *
 * Si un dossier musique est configuré (Settings.musicDir), le Music Import
 * Pipeline (tags, organisation Artiste/Album, rescan — §28) prend le relais
 * immédiatement. Sinon, dégrade proprement : les fichiers restent en
 * staging, récupérables manuellement (plan §2 — MUZZIK ne doit jamais
 * bloquer sur une config absente).
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function importCompletedTorrent(t: any): Promise<void> {
  const stagingDir = path.join(DOWNLOAD_COMPLETE_DIR, sanitizeName(t.name ?? t.infoHash));
  await fsp.mkdir(stagingDir, { recursive: true });

  for (const file of t.files ?? []) {
    const src = path.join(t.path, file.path);
    const dest = path.join(stagingDir, sanitizeName(path.basename(file.path)));
    try {
      await fsp.rename(src, dest);
    } catch {
      await fsp.copyFile(src, dest).catch((e) => console.error(`[import] copie échouée pour ${src}:`, e));
      await fsp.unlink(src).catch(() => {});
    }
  }

  const { musicDir } = getSettings();
  if (!musicDir) {
    console.log(`[import] pas de dossier musique configuré — fichiers laissés dans ${stagingDir}`);
    return;
  }

  const summary = await importMusicFromDownload(stagingDir, musicDir);
  console.log(
    `[import] ${summary.audioFilesImported}/${summary.filesScanned} fichiers importés dans ${musicDir} (${summary.failed.length} échecs)`
  );
}

function sanitizeName(name: string): string {
  return name.replace(/[/\\:]/g, "_").replace(/\.\.+/g, "_");
}
