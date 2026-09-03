import fs from "node:fs";
import fsp from "node:fs/promises";
import { Readable } from "node:stream";
import { getOfflineDownload } from "@/lib/library/offlineDownloadsStore";

export const dynamic = "force-dynamic";

/**
 * Lecture d'un morceau téléchargé hors ligne (pattern identique à
 * `src/app/api/stream/[fileId]/route.ts` pour les MediaFile scannés) — HTTP
 * range pour permettre le seek côté client player.
 *
 * Sécurité (plan §58) : le chemin servi vient UNIQUEMENT de l'entrée du
 * store, écrite par la route POST /api/offline — jamais reconstruit depuis
 * l'entrée client.
 */
export async function GET(req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const entry = getOfflineDownload(id);
  if (!entry || entry.state !== "COMPLETED" || !entry.filePath) {
    return new Response("Téléchargement hors ligne introuvable ou incomplet", { status: 404 });
  }

  let stat: fs.Stats;
  try {
    stat = await fsp.stat(entry.filePath);
  } catch {
    return new Response("Fichier absent du disque (téléchargement désynchronisé)", { status: 404 });
  }

  const range = req.headers.get("range");
  const mimeType = mimeForExtension(entry.filePath);

  if (!range) {
    const stream = fs.createReadStream(entry.filePath);
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

  const stream = fs.createReadStream(entry.filePath, { start, end });
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

function mimeForExtension(filePath: string): string {
  const ext = filePath.split(".").pop()?.toLowerCase() ?? "";
  const map: Record<string, string> = {
    flac: "audio/flac",
    mp3: "audio/mpeg",
    m4a: "audio/mp4",
    mp4: "audio/mp4",
    ogg: "audio/ogg",
    opus: "audio/opus",
    webm: "audio/webm",
    wav: "audio/wav",
  };
  return map[ext] ?? "application/octet-stream";
}
