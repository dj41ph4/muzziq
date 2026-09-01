import { NextResponse } from "next/server";
import { getSettings, updateSettings } from "@/lib/settings/store";

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json(getSettings());
}

export async function PATCH(req: Request) {
  const body = await req.json();
  const next = updateSettings(body);
  return NextResponse.json(next);
}
