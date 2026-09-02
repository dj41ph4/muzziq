import { NextResponse } from "next/server";
import { updatePlexConfig } from "@/lib/integrations/plex/store";

export const dynamic = "force-dynamic";

interface SelectBody {
  sections: { key: string; title: string }[];
}

export async function POST(req: Request) {
  const body: SelectBody = await req.json();
  if (!Array.isArray(body.sections)) return NextResponse.json({ error: "sections requis" }, { status: 400 });
  const next = updatePlexConfig({ musicSections: body.sections });
  return NextResponse.json({ musicSections: next.musicSections });
}
