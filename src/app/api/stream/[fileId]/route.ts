import fs from "node:fs";
import fsp from "node:fs/promises";
import { Readable } from "node:stream";
import { getMediaFile } from "@/lib/library/mediaFilesStore";

export const dynamic = "force-dynamic";

/**
 * Lecture locale (plan §82) : local FLAC trouvé → endpoint media MUZZIK →
 * HTTP range → client player. Pas de Plex.
 *
 * Sécurité (plan §58) : le chemin servi vient UNIQUEMENT
 * du store (écrit par le scanner), jamais d'un chemin reconstruit depuis
 * l'entrée client — `fileId` ne référence rien d'autre qu'une clé opaque.
 */
export async function GET(req: Request, { params }: { params: Promise<{ fileId: string }> }) {
  const { fileId } = await params;
  const file = getMediaFile(fileId);
  if (!file) {
    return new Response("Fichier introuvable", { status: 404 });
  }

  let stat: fs.Stats;
  try {
    stat = await fsp.stat(file.path);
  } catch {
    return new Response("Fichier absent du disque (bibliothèque désynchronisée — relancer un scan)", { status: 404 });
  }

  const range = req.headers.get("range");
  const mimeType = mimeForContainer(file.container);

  if (!range) {
    const stream = fs.createReadStream(file.path);
    return new Response(Readable.toWeb(stream) as ReadableStream, {
      status: 200,
      headers: {
        "Content-Type": mimeType,
        "Content-Length": String(stat.size),
        "Accept-Ranges": "bytes",
      },
    });
  }

  const match = /bytes=(\d+)-(\d*)/.exec(range);
  if (!match) {
    return new Response("Range invalide", { status: 416 });
  }
  const start = parseInt(match[1], 10);
  const end = match[2] ? parseInt(match[2], 10) : stat.size - 1;
  if (start >= stat.size || end >= stat.size || start > end) {
    return new Response("Range hors limites", { status: 416, headers: { "Content-Range": `bytes */${stat.size}` } });
  }

  const stream = fs.createReadStream(file.path, { start, end });
  return new Response(Readable.toWeb(stream) as ReadableStream, {
    status: 206,
    headers: {
      "Content-Type": mimeType,
      "Content-Length": String(end - start + 1),
      "Content-Range": `bytes ${start}-${end}/${stat.size}`,
      "Accept-Ranges": "bytes",
    },
  });
}

function mimeForContainer(container: string): string {
  const map: Record<string, string> = {
    flac: "audio/flac",
    mp3: "audio/mpeg",
    m4a: "audio/mp4",
    mp4: "audio/mp4",
    ogg: "audio/ogg",
    opus: "audio/opus",
    wav: "audio/wav",
  };
  return map[container.toLowerCase()] ?? "application/octet-stream";
}
