import { NextResponse } from "next/server";
import { getUserByUsername, createSession } from "@/lib/auth/store";
import { verifyPassword } from "@/lib/auth/password";
import { setSessionCookie } from "@/lib/auth/session";
import { toPublicUser } from "@/lib/auth/types";

export const dynamic = "force-dynamic";

export async function POST(req: Request) {
  const body = await req.json();
  const user = getUserByUsername(body.username ?? "");
  // Même message générique que le mot de passe soit faux ou l'utilisateur
  // inexistant — ne jamais révéler quels comptes existent.
  if (!user || !verifyPassword(body.password ?? "", user.passwordHash)) {
    return NextResponse.json({ error: "Identifiants invalides" }, { status: 401 });
  }
  const { token, expiresAt } = createSession(user.id);
  const res = NextResponse.json({ user: toPublicUser(user) });
  setSessionCookie(res, token, expiresAt);
  return res;
}
