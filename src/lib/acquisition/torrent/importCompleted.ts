import path from "node:path";
import fsp from "node:fs/promises";
import { DOWNLOAD_COMPLETE_DIR } from "./webTorrentBackend";

/**
 * Étape d'import minimale (plan §59/§28 partiel) : un torrent terminé n'est
 * jamais laissé dans le dossier de téléchargement incomplet — déplacé vers
 * `downloads/complete` une fois WebTorrent lui-même confirmé "done" (hash
 * vérifié). Ne va PAS jusqu'au Music Import Pipeline complet (tags, rename,
 * détection multi-disque, déplacement dans le dossier musique final) —
 * explicitement noté comme prochain chantier, pas fait ici.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function importCompletedTorrent(t: any): Promise<void> {
  const destDir = path.join(DOWNLOAD_COMPLETE_DIR, sanitizeName(t.name ?? t.infoHash));
  await fsp.mkdir(destDir, { recursive: true });

  for (const file of t.files ?? []) {
    const src = path.join(t.path, file.path);
    const dest = path.join(destDir, sanitizeName(path.basename(file.path)));
    try {
      await fsp.rename(src, dest);
    } catch {
      // rename échoue entre volumes différents — repli copie+suppression.
      await fsp.copyFile(src, dest).catch((e) => console.error(`[import] copie échouée pour ${src}:`, e));
      await fsp.unlink(src).catch(() => {});
    }
  }
}

function sanitizeName(name: string): string {
  return name.replace(/[/\\:]/g, "_").replace(/\.\.+/g, "_");
}
