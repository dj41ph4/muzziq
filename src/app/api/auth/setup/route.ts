import { NextResponse } from "next/server";
import { randomUUID } from "node:crypto";
import { hasAnyUser, addUser, createSession } from "@/lib/auth/store";
import { hashPassword } from "@/lib/auth/password";
import { setSessionCookie } from "@/lib/auth/session";
import { toPublicUser } from "@/lib/auth/types";

export const dynamic = "force-dynamic";

/** Premier compte créé = admin (plan — pas de compte forcé, mais le premier utilisateur devient admin s'il choisit de configurer l'auth). */
export async function POST(req: Request) {
  if (hasAnyUser()) return NextResponse.json({ error: "Configuration déjà terminée" }, { status: 409 });

  const body = await req.json();
  const username: string = body.username?.trim();
  const password: string = body.password;
  if (!username || !password || password.length < 8) {
    return NextResponse.json({ error: "Nom d'utilisateur requis, mot de passe d'au moins 8 caractères" }, { status: 400 });
  }

  const user = addUser({ id: randomUUID(), username, passwordHash: hashPassword(password), role: "admin", createdAt: Date.now() });
  const { token, expiresAt } = createSession(user.id);
  const res = NextResponse.json({ user: toPublicUser(user) });
  setSessionCookie(res, token, expiresAt);
  return res;
}
