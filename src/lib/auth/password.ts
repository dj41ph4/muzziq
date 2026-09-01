import { randomBytes, scryptSync, timingSafeEqual } from "node:crypto";

/**
 * Hashage de mot de passe via scrypt (natif Node, aucune dépendance externe —
 * porté à l'identique depuis Movviz, générique). Stocké "salt:hash", hex.
 */

export function hashPassword(password: string): string {
  const salt = randomBytes(16).toString("hex");
  const hash = scryptSync(password, salt, 64).toString("hex");
  return `${salt}:${hash}`;
}

export function verifyPassword(password: string, stored: string): boolean {
  const [salt, hash] = stored.split(":");
  if (!salt || !hash) return false;
  const hashBuf = Buffer.from(hash, "hex");
  const testBuf = scryptSync(password, salt, 64);
  return hashBuf.length === testBuf.length && timingSafeEqual(hashBuf, testBuf);
}
