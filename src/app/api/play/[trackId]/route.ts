import { NextResponse } from "next/server";
import { youtubeMusicProvider } from "@/providers/youtube-music";

export const dynamic = "force-dynamic";

/**
 * Playback Resolver minimal (plan §12) — le client demande un trackId, jamais
 * un ID provider brut. À ce stade il n'y a qu'une source (YouTube Music,
 * actuellement AUTH_REQUIRED faute de PoToken) — la logique LOCAL > CACHE >
 * PROVIDER viendra avec la Phase C (bibliothèque locale).
 */
export async function GET(_req: Request, { params }: { params: Promise<{ trackId: string }> }) {
  const { trackId } = await params;
  const resolution = await youtubeMusicProvider.resolvePlayback(trackId);

  if (resolution.ok) {
    return NextResponse.json(resolution.source);
  }
  return NextResponse.json({ error: resolution.reason, status: resolution.status }, { status: 503 });
}
