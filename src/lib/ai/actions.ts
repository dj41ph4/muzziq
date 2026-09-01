import { youtubeMusicProvider } from "@/providers/youtube-music";
import { findOrCreateRecordingFromExternal } from "@/lib/library/recordingResolution";
import { addLibraryItem } from "@/lib/library/libraryItemsStore";
import { listRecordings } from "@/lib/library/recordingsStore";

/**
 * Action Engine MUZZIK AI (plan §45). L'IA ne fabrique jamais un résultat —
 * elle ne fait que demander une action, exécutée ici contre les vraies
 * fonctions MUZZIK ; le modèle ne voit que le résultat réel. "Avant de dire
 * qu'un morceau n'est pas disponible, l'IA doit interroger MUZZIK" — c'est
 * exactement le rôle de ce module : jamais de réponse inventée sur l'état
 * du catalogue/de la bibliothèque.
 */

export interface AiIntent {
  action: "search" | "add_to_library" | "none";
  query?: string;
  providerTrackId?: string;
}

export interface AiActionResult {
  action: AiIntent["action"];
  ok: boolean;
  detail: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  data?: any;
}

export async function executeAiIntent(intent: AiIntent): Promise<AiActionResult> {
  switch (intent.action) {
    case "search": {
      if (!intent.query) return { action: "search", ok: false, detail: "Requête manquante" };
      try {
        const result = await youtubeMusicProvider.search({ text: intent.query, scope: "songs" });
        return { action: "search", ok: true, detail: `${result.tracks.length} résultats`, data: result.tracks.slice(0, 5) };
      } catch (e) {
        return { action: "search", ok: false, detail: e instanceof Error ? e.message : String(e) };
      }
    }
    case "add_to_library": {
      if (!intent.providerTrackId) return { action: "add_to_library", ok: false, detail: "providerTrackId manquant" };
      // Le modèle ne fournit qu'un ID déjà vu dans un résultat de recherche
      // réel juste avant — jamais un ID inventé de toutes pièces (le prompt
      // système impose de toujours chercher avant d'ajouter).
      const result = await youtubeMusicProvider.search({ text: intent.query ?? "", scope: "songs" });
      const track = result.tracks.find((t) => t.providerTrackId === intent.providerTrackId);
      if (!track) return { action: "add_to_library", ok: false, detail: "Morceau introuvable dans les derniers résultats" };
      const recording = findOrCreateRecordingFromExternal({
        provider: "youtube-music",
        providerTrackId: track.providerTrackId,
        title: track.title,
        artist: track.artist,
        album: track.album,
        durationSeconds: track.durationSeconds,
        thumbnailUrl: track.thumbnailUrl,
      });
      addLibraryItem(recording.id, "STREAM_ONLY");
      return { action: "add_to_library", ok: true, detail: `${track.title} ajouté à la bibliothèque` };
    }
    default:
      return { action: "none", ok: true, detail: "" };
  }
}

/** État réel de la bibliothèque injecté dans le prompt — jamais une hallucination du modèle. */
export function summarizeLibraryForPrompt(): string {
  const recordings = listRecordings();
  if (recordings.length === 0) return "Bibliothèque vide pour l'instant.";
  return `${recordings.length} morceaux connus, derniers : ${recordings
    .slice(-10)
    .map((r) => `${r.title} — ${r.artist}`)
    .join(" ; ")}`;
}
