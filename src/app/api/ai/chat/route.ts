import { NextResponse } from "next/server";
import { getAiConfig } from "@/lib/ai/store";
import { callAi } from "@/lib/ai/providers";
import { buildUnifiedUserContextSnapshot, formatUnifiedUserContext } from "@/lib/userContext/query";
import { DEFAULT_USER_ID } from "@/lib/userContext/types";
import { executeAiIntent, summarizeLibraryForPrompt, type AiIntent } from "@/lib/ai/actions";

export const dynamic = "force-dynamic";

const SYSTEM_PROMPT = `Tu es MuzziQ AI, l'assistant intégré à MuzziQ, une plateforme musicale personnelle.

Règle absolue : tu ne dois JAMAIS inventer si un morceau est disponible, dans la bibliothèque, ou son statut — utilise UNIQUEMENT le contexte réel fourni ci-dessous et les actions listées. Si tu n'es pas sûr, cherche avant de répondre.

Réponds STRICTEMENT avec un objet JSON de cette forme, rien d'autre, pas de markdown :
{"reply": "ta réponse en français, naturelle et courte", "action": {"action": "search"|"add_to_library"|"none", "query": "...", "providerTrackId": "..."}}

- "search" : cherche un morceau/artiste sur YouTube Music.
- "add_to_library" : ajoute un morceau déjà vu dans un résultat de recherche récent (providerTrackId exact requis) — ne jamais inventer un ID.
- "none" : pas d'action, juste une réponse conversationnelle.`;

export async function POST(req: Request) {
  const config = getAiConfig();
  if (!config.enabled) {
    return NextResponse.json({ error: "MuzziQ AI désactivé — active-le dans Réglages > IA" }, { status: 400 });
  }

  const body = await req.json();
  const message: string = body.message;
  if (!message?.trim()) return NextResponse.json({ error: "message requis" }, { status: 400 });

  const snapshot = await buildUnifiedUserContextSnapshot(DEFAULT_USER_ID);
  const contextText = formatUnifiedUserContext(snapshot) || "Aucun historique pour l'instant.";
  const libraryText = summarizeLibraryForPrompt();

  const system = `${SYSTEM_PROMPT}\n\nContexte utilisateur réel : ${contextText}\nBibliothèque : ${libraryText}`;

  try {
    const { text, provider } = await callAi(config, system, [{ role: "user", content: message }]);

    let parsed: { reply: string; action?: AiIntent };
    try {
      parsed = JSON.parse(text);
    } catch {
      // Le modèle n'a pas respecté le format JSON demandé — dégrade en
      // réponse brute plutôt que de planter (jamais fiable de forcer un
      // modèle à toujours respecter un format).
      parsed = { reply: text };
    }

    let actionResult = null;
    if (parsed.action && parsed.action.action !== "none") {
      actionResult = await executeAiIntent(parsed.action);
    }

    return NextResponse.json({ reply: parsed.reply, provider, actionResult });
  } catch (e) {
    return NextResponse.json({ error: e instanceof Error ? e.message : String(e) }, { status: 502 });
  }
}
