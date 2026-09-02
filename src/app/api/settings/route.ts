import { NextRequest, NextResponse } from "next/server";
import { getSettings, updateSettings } from "@/lib/settings/store";
import { requireAdmin } from "@/lib/auth/guard";
import { hasAnyUser } from "@/lib/auth/store";

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json(getSettings());
}

/**
 * Admin uniquement — mais seulement une fois qu'un compte a été créé
 * (hasAnyUser()). Tant que l'auth n'est pas configurée (aucun compte),
 * MuzziQ reste utilisable sans login (plan §2 — jamais bloquant sur une
 * config absente) ; dès qu'un premier compte existe, les réglages serveur
 * exigent d'être connecté en admin.
 */
export async function PATCH(req: NextRequest) {
  if (hasAnyUser() && !requireAdmin(req)) {
    return NextResponse.json({ error: "Authentification admin requise" }, { status: 401 });
  }
  const body = await req.json();
  const next = updateSettings(body);
  return NextResponse.json(next);
}
