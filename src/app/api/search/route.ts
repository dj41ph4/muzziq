import { NextResponse } from "next/server";
import { youtubeMusicProvider } from "@/providers/youtube-music";
import { listMediaFiles } from "@/lib/library/mediaFilesStore";
import { resolveLocalMatch } from "@/lib/identity/resolver";

export const dynamic = "force-dynamic";

export async function GET(req: Request) {
  const { searchParams } = new URL(req.url);
  const q = searchParams.get("q");
  if (!q || q.trim().length === 0) {
    return NextResponse.json({ error: "Paramètre q requis" }, { status: 400 });
  }

  try {
    const result = await youtubeMusicProvider.search({ text: q, scope: "songs" });

    // Availability Engine minimal (plan §10) : pour chaque résultat, vérifier
    // s'il existe déjà en local. C'est le cœur du vertical slice Phase C —
    // "même morceau, source stream + source locale, MUZZIK choisit le local".
    const localFiles = listMediaFiles();
    const tracks = result.tracks.map((track) => {
      const match = resolveLocalMatch(track, localFiles);
      return {
        ...track,
        localMatch: match ? { fileId: match.mediaFile.id, confidence: match.confidence } : undefined,
      };
    });

    return NextResponse.json({ ...result, tracks });
  } catch (err) {
    // Un provider externe en panne ne doit jamais faire planter la route —
    // toujours une réponse structurée (plan §74/§76).
    return NextResponse.json({ error: "Recherche indisponible", detail: String(err) }, { status: 502 });
  }
}
