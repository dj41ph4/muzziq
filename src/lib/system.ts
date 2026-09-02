import fs from "node:fs";
import path from "node:path";

/**
 * Aides système côté serveur pour l'explorateur de dossiers des réglages
 * (porté depuis Movviz src/lib/system.ts — logique générique, aucune
 * dépendance au domaine cinéma).
 *
 * MuzziQ est auto-hébergé : l'explorateur doit parcourir le système de
 * fichiers du SERVEUR, pas celui du navigateur — c'est ce qui permet à un
 * utilisateur NAS/Docker de VOIR les dossiers réellement montés dans le
 * conteneur (ex. /music) au lieu de deviner un chemin à l'aveugle. Listing
 * de dossiers uniquement, jamais le contenu des fichiers.
 */

export function isContainer(): boolean {
  if (process.env.MUZZIQ_CONTAINER === "1") return true;
  try {
    return fs.existsSync("/.dockerenv") || fs.existsSync("/run/.containerenv");
  } catch {
    return false;
  }
}

export function systemInfo() {
  return { platform: process.platform, isContainer: isContainer(), sep: path.sep };
}

/** Liste les racines de lecteurs sous Windows (C:\, D:\...). */
export function listDrives(): string[] {
  const drives: string[] = [];
  for (let c = 65; c <= 90; c++) {
    const root = `${String.fromCharCode(c)}:\\`;
    try {
      fs.accessSync(root);
      drives.push(root);
    } catch {
      /* absent */
    }
  }
  return drives;
}

export interface DirListing {
  path: string;
  parent: string | null;
  isRoot: boolean;
  drives: string[];
  dirs: { name: string; path: string }[];
}

/** Liste les sous-dossiers d'un chemin (pour l'explorateur). */
export function listDirs(target: string): DirListing {
  const dir = target && target.trim() ? target : process.platform === "win32" ? "" : "/";

  if (!dir && process.platform === "win32") {
    return { path: "", parent: null, isRoot: true, drives: listDrives(), dirs: [] };
  }

  const resolved = path.resolve(dir);
  let entries: string[] = [];
  try {
    entries = fs
      .readdirSync(resolved, { withFileTypes: true })
      .filter((d) => d.isDirectory())
      .map((d) => d.name)
      .filter((n) => !n.startsWith("$"))
      .sort((a, b) => a.localeCompare(b));
  } catch {
    entries = [];
  }

  const parent = path.dirname(resolved);
  const atRoot = parent === resolved;

  return {
    path: resolved,
    parent: atRoot ? (process.platform === "win32" ? "" : null) : parent,
    isRoot: false,
    drives: process.platform === "win32" ? listDrives() : [],
    dirs: entries.map((name) => ({ name, path: path.join(resolved, name) })),
  };
}

/** Vérifie qu'un dossier existe et est accessible en lecture+écriture — jamais un save silencieusement optimiste. */
export function checkFolderAccess(dir: string): { ok: boolean; error: string | null } {
  try {
    fs.accessSync(dir, fs.constants.R_OK | fs.constants.W_OK);
    const stat = fs.statSync(dir);
    if (!stat.isDirectory()) return { ok: false, error: "Le chemin existe mais n'est pas un dossier" };
    return { ok: true, error: null };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}
