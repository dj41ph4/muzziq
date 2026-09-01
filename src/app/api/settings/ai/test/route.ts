import { NextResponse } from "next/server";
import { testProviderKey } from "@/lib/ai/providers";
import type { AiProviderId } from "@/lib/ai/types";

export const dynamic = "force-dynamic";

export async function POST(req: Request) {
  const body = await req.json();
  const { providerId, model, key } = body as { providerId: AiProviderId; model: string; key: string };
  if (!providerId || !key) {
    return NextResponse.json({ ok: false, detail: "providerId et key requis" }, { status: 400 });
  }
  const result = await testProviderKey(providerId, model, key);
  return NextResponse.json(result);
}
