import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";
import { checkFolderAccess } from "@/lib/system";

/**
 * Premier store — démontre le pattern que tout futur store MuzziQ doit
 * suivre (plan §105.1/§105.9) : jamais d'accès fichier direct, toujours
 * readJsonCached/writeJsonCached.
 */

const FILE = dataFile("settings.json");

export interface MuzziqSettings {
  serverName: string;
  musicDir: string | null;
  /** Erreur persistée si le dernier `musicDir` enregistré était inaccessible — jamais un save silencieusement optimiste (plan §105 recherche Movviz). */
  musicDirError: string | null;
}

const DEFAULT: MuzziqSettings = {
  serverName: "MuzziQ",
  musicDir: null,
  musicDirError: null,
};

export function getSettings(): MuzziqSettings {
  return { ...DEFAULT, ...readJsonCached<Partial<MuzziqSettings>>(FILE, {}) };
}

export function updateSettings(patch: Partial<MuzziqSettings>): MuzziqSettings {
  const current = getSettings();
  const next = { ...current, ...patch };

  // Vérification réelle dès que musicDir change — jamais juste accepter la
  // valeur sans tester l'accès (incident Movviz documenté : un save "réussi"
  // ne prouve rien tant que le dossier n'est pas réellement lu/écrit).
  if (patch.musicDir !== undefined && patch.musicDir !== current.musicDir) {
    next.musicDirError = next.musicDir ? checkFolderAccess(next.musicDir).error : null;
  }

  writeJsonCached(FILE, next);
  return next;
}
