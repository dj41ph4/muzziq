import { NextRequest, NextResponse } from "next/server";
import { listDirs } from "@/lib/system";
import { requireAdmin } from "@/lib/auth/guard";
import { hasAnyUser } from "@/lib/auth/store";

export const dynamic = "force-dynamic";

/**
 * Liste les sous-dossiers du serveur (plan — explorateur de dossiers pour
 * les réglages). Admin uniquement une fois l'auth configurée (même règle
 * que /api/settings) — l'énumération du système de fichiers du serveur
 * est sensible, jamais ouverte sans réflexion.
 */
export async function GET(req: NextRequest) {
  if (hasAnyUser() && !requireAdmin(req)) {
    return NextResponse.json({ error: "Authentification admin requise" }, { status: 401 });
  }
  const { searchParams } = new URL(req.url);
  const path = searchParams.get("path") ?? "";
  return NextResponse.json(listDirs(path));
}
