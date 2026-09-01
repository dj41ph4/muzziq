import { NextResponse } from "next/server";
import { youtubeMusicProvider } from "@/providers/youtube-music";

export const dynamic = "force-dynamic";

export async function GET(req: Request) {
  const { searchParams } = new URL(req.url);
  const q = searchParams.get("q");
  if (!q || q.trim().length === 0) {
    return NextResponse.json({ error: "Paramètre q requis" }, { status: 400 });
  }

  try {
    const result = await youtubeMusicProvider.search({ text: q, scope: "songs" });
    return NextResponse.json(result);
  } catch (err) {
    // Un provider externe en panne ne doit jamais faire planter la route —
    // toujours une réponse structurée (plan §74/§76).
    return NextResponse.json({ error: "Recherche indisponible", detail: String(err) }, { status: 502 });
  }
}
