import { NextRequest, NextResponse } from "next/server";
import { getAiConfig, updateAiConfig } from "@/lib/ai/store";
import { AI_PROVIDER_INFO } from "@/lib/ai/types";
import { requireAdmin } from "@/lib/auth/guard";
import { hasAnyUser } from "@/lib/auth/store";

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({ config: getAiConfig(), providerInfo: AI_PROVIDER_INFO });
}

/** Admin uniquement une fois l'auth configurée — mêmes règles que /api/settings. */
export async function PATCH(req: NextRequest) {
  if (hasAnyUser() && !requireAdmin(req)) {
    return NextResponse.json({ error: "Authentification admin requise" }, { status: 401 });
  }
  const body = await req.json();
  const next = updateAiConfig(body);
  return NextResponse.json({ config: next });
}
