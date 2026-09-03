import { resolveViaPotUmp } from "@/providers/youtube-music/potUmpResolver";

export const dynamic = "force-dynamic";

/**
 * Sert les octets audio déjà extraits par le chemin PoToken + UMP (voir
 * `potUmpResolver.ts`). Le résultat étant mis en cache par ce module
 * (quelques minutes, le temps que le lecteur consomme le flux), cet appel
 * ne relance le pipeline (navigateur headless + fetch + parse UMP) que si le
 * cache a expiré ou que la résolution précédente n'est pas passée par ce
 * chemin. Ne construit jamais de réponse "OK" fabriquée : si la résolution
 * échoue ici, c'est un vrai 404, jamais un flux vide déguisé en succès.
 */
export async function GET(_req: Request, { params }: { params: Promise<{ videoId: string }> }) {
  const { videoId } = await params;

  const result = await resolveViaPotUmp(videoId);
  if (!result) {
    return new Response("Flux audio indisponible via le chemin PoToken/UMP pour ce morceau", { status: 404 });
  }

  return new Response(new Uint8Array(result.audioBytes), {
    status: 200,
    headers: {
      "Content-Type": result.contentType,
      "Content-Length": String(result.audioBytes.length),
      "Cache-Control": "private, max-age=60",
      "Accept-Ranges": "none",
    },
  });
}
