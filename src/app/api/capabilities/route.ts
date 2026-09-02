import { NextResponse } from "next/server";
import { listIndexers } from "@/lib/acquisition/indexers/store";
import { getPlexConfig, plexIsConnected } from "@/lib/integrations/plex/store";
import { getSettings } from "@/lib/settings/store";

export const dynamic = "force-dynamic";

/**
 * Négociation de capacités serveur (plan Android §83/§9) — le client (Android
 * ou autre) ne doit jamais SUPPOSER qu'un serveur MuzziQ possède telle ou
 * telle capacité : il l'interroge. Chaque champ reflète un état réellement
 * vérifiable au moment de l'appel, jamais une intention ou une config
 * déclarée mais inopérante.
 */
export async function GET() {
  const settings = getSettings();
  const indexers = listIndexers();
  const plex = getPlexConfig();

  const nasLibrary = !!settings.musicDir && !settings.musicDirError;
  const torrentAcquisition = indexers.some((i) => i.enabled !== false);
  // FLAC est acquis via le même pipeline indexers→torrent→import — pas une
  // capacité séparée côté serveur, juste torrentAcquisition + un dossier
  // musique valide pour recevoir l'import.
  const flacAcquisition = torrentAcquisition && nasLibrary;

  return NextResponse.json({
    server: { name: settings.serverName, version: process.env.npm_package_version ?? "0.1.0" },
    capabilities: {
      flacAcquisition,
      torrentAcquisition,
      nasLibrary,
      monitoring: false, // pas encore implémenté côté serveur (voir CLAUDE.md — chantiers restants)
      automaticUpgrade: false,
      centralSync: plexIsConnected(plex) && plex.syncPolicy !== "OFF",
      remoteJam: false,
      plexIntegration: plexIsConnected(plex),
    },
  });
}
