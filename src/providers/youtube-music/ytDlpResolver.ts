import { execFile } from "node:child_process";
import { promisify } from "node:util";
import fs from "node:fs";

const execFileAsync = promisify(execFile);

/**
 * Résolution de flux via yt-dlp en subprocess externe (décision documentée
 * dans le plan §105 / réflexion d'architecture 2026-09-01).
 *
 * InnerTube en anonyme (playbackResolver.ts) est bloqué sans PoToken sur
 * tous les contextes client testés (voir docs/reverse-engineering).
 * Réimplémenter un solveur BotGuard maison serait reconstruire — seul,
 * sans budget de maintenance continue — ce que des projets entiers
 * (yt-dlp, ytmusicapi) maintiennent déjà. yt-dlp est une librairie mature
 * à licence adaptée (Unlicense) qui résout ce problème précis : réutilisée
 * telle quelle en subprocess, conformément à la règle §87.4 du plan
 * ("ne pas réinventer la roue" — utiliser une librairie mature existante).
 *
 * Ce n'est PAS aria2 (INTERDIT 5 du plan) : aria2 est un backend de
 * téléchargement de FICHIERS, jamais utilisé ici. yt-dlp n'est invoqué
 * qu'en mode "get URL" (--get-url), aucun fichier n'est jamais écrit sur
 * disque par ce module.
 */

function resolveBinaryPath(): string {
  if (process.env.MUZZIQ_YT_DLP_PATH) return process.env.MUZZIQ_YT_DLP_PATH;
  // Chemin d'installation pip par défaut sur cette machine de dev (Windows,
  // Python non ajouté au PATH pour les scripts). En Docker/prod, `yt-dlp`
  // est attendu sur le PATH (image avec le paquet installé) — ce fallback
  // n'est qu'une commodité locale, jamais supposé exister ailleurs.
  const devFallback = "C:/Users/dj41ph4/AppData/Local/Programs/Python/Python312/Scripts/yt-dlp.exe";
  if (fs.existsSync(devFallback)) return devFallback;
  return "yt-dlp";
}

export interface YtDlpAudioFormat {
  url: string;
  ext: string;
  abr?: number;
  acodec?: string;
}

/** Résout la meilleure URL de flux audio directe pour un videoId, ou lève une erreur explicite. */
export async function resolveStreamUrl(videoId: string): Promise<YtDlpAudioFormat> {
  const bin = resolveBinaryPath();
  try {
    const { stdout } = await execFileAsync(
      bin,
      ["-f", "bestaudio", "-j", "--no-playlist", "--no-warnings", `https://music.youtube.com/watch?v=${videoId}`],
      { timeout: 15000, maxBuffer: 20 * 1024 * 1024 }
    );
    const info = JSON.parse(stdout);
    if (!info.url) throw new Error("yt-dlp n'a renvoyé aucune URL de flux");
    return { url: info.url, ext: info.ext, abr: info.abr, acodec: info.acodec };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    throw new Error(`yt-dlp a échoué pour ${videoId}: ${message}`);
  }
}
