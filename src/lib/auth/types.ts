/**
 * Utilisateurs MUZZIK (porté depuis Movviz src/lib/auth, simplifié — pas de
 * champs Plex/requêtes spécifiques à Movviz, pas pertinents ici).
 */

export type UserRole = "admin" | "user";

export interface User {
  id: string;
  username: string;
  passwordHash: string;
  role: UserRole;
  createdAt: number;
}

/** Jamais envoyer passwordHash au navigateur. */
export type PublicUser = Omit<User, "passwordHash">;

export function toPublicUser(u: User): PublicUser {
  const { passwordHash: _passwordHash, ...rest } = u;
  return rest;
}
