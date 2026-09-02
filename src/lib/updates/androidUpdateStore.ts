import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";

/**
 * Mise à jour auto-hébergée du client Android (plan §56.3). Rempli manuellement
 * pour l'instant — aucune UI admin ne l'écrit encore (voir android-mobile/ dans
 * le rapport de la Phase I) ; c'est un chantier suivant, pas caché ici. Tant
 * que ce store est vide, GET /api/updates/android répond "pas de mise à jour",
 * jamais une erreur — cohérent avec le principe "jamais bloquant sur une
 * config absente" (règle absolue du dépôt, §2).
 */

const FILE = dataFile("android-update.json");

export interface AndroidUpdateInfo {
  latestVersionCode: number;
  latestVersionName: string;
  /** URL directe vers l'APK — typiquement l'asset d'une release GitHub du dépôt. */
  apkUrl: string;
  changelog?: string;
}

export function getAndroidUpdate(): AndroidUpdateInfo | null {
  return readJsonCached<AndroidUpdateInfo | null>(FILE, null);
}

export function setAndroidUpdate(info: AndroidUpdateInfo): AndroidUpdateInfo {
  writeJsonCached(FILE, info);
  return info;
}
