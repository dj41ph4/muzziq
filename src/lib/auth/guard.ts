import type { NextRequest } from "next/server";
import { getCurrentUser } from "./session";
import type { User } from "./types";

/**
 * Garde de session — porté depuis Movviz src/lib/auth/guard.ts, simplifié
 * (pas de token API bearer, pas de suivi d'activité — pas encore construits
 * côté MuzziQ, ajoutables plus tard sans changer cette signature).
 */
export function requireUser(req: NextRequest): User | null {
  return getCurrentUser(req);
}

export function requireAdmin(req: NextRequest): User | null {
  const u = requireUser(req);
  return u && u.role === "admin" ? u : null;
}
