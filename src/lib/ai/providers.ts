import type { AiConfig, AiProviderId } from "./types";
import { AI_PROVIDER_ORDER } from "./types";

/**
 * Client LLM multi-fournisseur (porté depuis Movviz, mêmes URLs/logique) —
 * rotation de clés par fournisseur + repli entre fournisseurs. Aucune URL
 * fournie par l'utilisateur n'est jamais fetchée ici (constantes en dur) —
 * pas de surface SSRF.
 */

const TIMEOUT_MS = 45_000;
const MAX_RESPONSE_TOKENS = 4096;
const QUOTA_RE = /quota|rate limit|resource exhausted|insufficient_quota|429|too many requests|403|forbidden|invalid api key|api key not valid/i;

export class AiCallError extends Error {
  readonly provider: AiProviderId;
  readonly quota: boolean;
  constructor(provider: AiProviderId, message: string, quota: boolean) {
    super(message);
    this.name = "AiCallError";
    this.provider = provider;
    this.quota = quota;
  }
}

export interface AiChatMessage {
  role: "user" | "assistant";
  content: string;
}

async function jsonFetch(
  providerId: AiProviderId,
  url: string,
  headers: Record<string, string>,
  body: unknown
): Promise<unknown> {
  const res = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(TIMEOUT_MS),
    cache: "no-store",
  });
  const raw = await res.text();
  let json: unknown = null;
  try {
    json = raw ? JSON.parse(raw) : null;
  } catch {
    /* corps d'erreur non-JSON */
  }
  const errorMessage = (() => {
    try {
      const j = JSON.parse(raw) as { error?: { message?: string } | string; message?: string };
      const m = typeof j.error === "string" ? j.error : j.error?.message ?? j.message;
      return typeof m === "string" ? m : null;
    } catch {
      return null;
    }
  })();
  if (!res.ok) {
    if (res.status === 429 || res.status === 403 || (errorMessage && QUOTA_RE.test(errorMessage))) {
      throw new AiCallError(providerId, errorMessage ?? `HTTP ${res.status}`, true);
    }
    throw new AiCallError(providerId, errorMessage ?? `HTTP ${res.status} (${res.statusText})`, false);
  }
  return json ?? raw;
}

function toOpenAiMessages(messages: AiChatMessage[]) {
  return messages.map((m) => ({ role: m.role, content: m.content }));
}

async function callWithKey(
  providerId: AiProviderId,
  url: string,
  headers: Record<string, string>,
  body: unknown
): Promise<string> {
  const json = await jsonFetch(providerId, url, headers, body);
  let text = "";
  if (providerId === "gemini") {
    const cands = (json as { candidates?: { content?: { parts?: { text?: string }[] } }[] })?.candidates ?? [];
    text = cands.map((c) => (c.content?.parts ?? []).map((p) => p.text ?? "").join("")).join("");
  } else {
    const choices = (json as { choices?: { message?: { content?: string } }[] })?.choices ?? [];
    text = choices.map((c) => c.message?.content ?? "").join("");
  }
  return text.trim();
}

async function callProvider(
  config: AiConfig,
  providerId: AiProviderId,
  system: string,
  messages: AiChatMessage[]
): Promise<string> {
  const provider = config.providers[providerId];
  const model =
    provider.model.trim() ||
    (providerId === "mistral" ? "mistral-small-latest" : providerId === "openrouter" ? "deepseek/deepseek-chat" : "gemini-2.5-flash-lite");
  const keys = provider.keys.filter((k) => k.key.trim().length > 0);
  if (keys.length === 0) throw new AiCallError(providerId, "Aucune clé API configurée pour ce fournisseur", false);

  let lastError: AiCallError | null = null;
  for (const entry of keys) {
    const key = entry.key.trim();
    try {
      if (providerId === "gemini") {
        const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(key)}`;
        return await callWithKey(providerId, url, { "content-type": "application/json" }, {
          systemInstruction: { parts: [{ text: system }] },
          contents: messages.map((m) => ({ role: m.role === "assistant" ? "model" : "user", parts: [{ text: m.content }] })),
          generationConfig: { temperature: 0.2, maxOutputTokens: MAX_RESPONSE_TOKENS },
        });
      }
      const url = providerId === "mistral" ? "https://api.mistral.ai/v1/chat/completions" : "https://openrouter.ai/api/v1/chat/completions";
      const headers: Record<string, string> = {
        "content-type": "application/json",
        authorization: `Bearer ${key}`,
        ...(providerId === "openrouter" ? { "X-Title": "MUZZIK" } : {}),
      };
      return await callWithKey(providerId, url, headers, {
        model,
        messages: [{ role: "system", content: system }, ...toOpenAiMessages(messages)],
        temperature: 0.2,
        max_tokens: MAX_RESPONSE_TOKENS,
      });
    } catch (e) {
      lastError = e instanceof AiCallError ? e : new AiCallError(providerId, (e as Error).message, false);
    }
  }
  throw lastError ?? new AiCallError(providerId, "Échec inconnu", false);
}

/** Chaîne configurée : fournisseur primaire d'abord, puis repli si activé. */
export async function callAi(config: AiConfig, system: string, messages: AiChatMessage[]): Promise<{ text: string; provider: AiProviderId }> {
  const order: AiProviderId[] = [config.primary, ...AI_PROVIDER_ORDER.filter((p) => p !== config.primary)];
  const chain = config.fallback ? order : [config.primary];

  let lastError: AiCallError | null = null;
  for (const providerId of chain) {
    try {
      const text = await callProvider(config, providerId, system, messages);
      if (text) return { text, provider: providerId };
      lastError = new AiCallError(providerId, "Réponse vide du modèle", false);
    } catch (e) {
      lastError = e instanceof AiCallError ? e : new AiCallError(providerId, (e as Error).message, false);
    }
  }
  throw lastError ?? new AiCallError(config.primary, "Aucun fournisseur disponible", false);
}

/** Teste une seule clé avec un prompt minimal — utilisé par le bouton "Tester" des réglages. */
export async function testProviderKey(providerId: AiProviderId, model: string, key: string): Promise<{ ok: boolean; detail: string }> {
  const config: AiConfig = {
    enabled: true,
    primary: providerId,
    fallback: false,
    providers: {
      mistral: { model: providerId === "mistral" ? model : "mistral-small-latest", keys: providerId === "mistral" ? [{ id: "t", key }] : [] },
      openrouter: { model: providerId === "openrouter" ? model : "deepseek/deepseek-chat", keys: providerId === "openrouter" ? [{ id: "t", key }] : [] },
      gemini: { model: providerId === "gemini" ? model : "gemini-2.5-flash-lite", keys: providerId === "gemini" ? [{ id: "t", key }] : [] },
    },
  };
  try {
    const { text } = await callAi(config, "Réponds uniquement par le mot OK.", [{ role: "user", content: "test" }]);
    return { ok: true, detail: text.slice(0, 80) || "Réponse vide mais requête acceptée" };
  } catch (e) {
    return { ok: false, detail: e instanceof Error ? e.message : String(e) };
  }
}
