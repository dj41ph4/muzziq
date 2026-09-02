import { NextResponse } from "next/server";
import { getAndroidUpdate } from "@/lib/updates/androidUpdateStore";

export const dynamic = "force-dynamic";

/**
 * Consommé par android-mobile/ (UpdateChecker.kt) en mode Lié uniquement
 * (plan §56.3) — le client compare latestVersionCode à sa propre version et
 * propose une bannière, jamais une mise à jour forcée. 404 tant qu'aucune
 * version n'a été publiée ici : le client traite ça comme "pas de mise à
 * jour disponible", pas comme une erreur.
 */
export async function GET() {
  const info = getAndroidUpdate();
  if (!info) {
    return NextResponse.json({ error: "Aucune mise à jour Android publiée" }, { status: 404 });
  }
  return NextResponse.json(info);
}
