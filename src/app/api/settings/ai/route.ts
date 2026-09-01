import { NextResponse } from "next/server";
import { getAiConfig, updateAiConfig } from "@/lib/ai/store";
import { AI_PROVIDER_INFO } from "@/lib/ai/types";

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({ config: getAiConfig(), providerInfo: AI_PROVIDER_INFO });
}

export async function PATCH(req: Request) {
  const body = await req.json();
  const next = updateAiConfig(body);
  return NextResponse.json({ config: next });
}
