import { NextResponse } from "next/server";
import path from "node:path";
import fs from "node:fs";
import { dataFile } from "@/lib/config";
import { getOfflineDownload, removeOfflineDownload } from "@/lib/library/offlineDownloadsStore";

export const dynamic = "force-dynamic";

const OFFLINE_DIR = path.resolve(dataFile("offline"));

export async function DELETE(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const entry = getOfflineDownload(id);
  if (!entry) return NextResponse.json({ error: "Introuvable" }, { status: 404 });

  // Seul un fichier réellement téléchargé (sourceKind "provider", vivant sous
  // DATA_DIR/offline) est supprimé du disque. Un "local" pointe vers un
  // MediaFile de la bibliothèque scannée — jamais touché par cette route
  // (sécurité fichier INTERDIT 6 : vérification de dossier attendu avant
  // toute suppression).
  if (entry.sourceKind === "provider" && entry.filePath) {
    const resolved = path.resolve(entry.filePath);
    if (resolved.startsWith(OFFLINE_DIR + path.sep)) {
      try {
        await fs.promises.unlink(resolved);
      } catch (err) {
        const code = (err as NodeJS.ErrnoException).code;
        if (code !== "ENOENT") {
          return NextResponse.json({ error: `Suppression du fichier échouée : ${String(err)}` }, { status: 500 });
        }
      }
    }
  }

  removeOfflineDownload(id);
  return NextResponse.json({ ok: true });
}
