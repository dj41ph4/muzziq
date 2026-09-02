/**
 * Config IA multi-fournisseur (plan §45, MuzziQ AI = équivalent Movviz AI).
 * Porté directement depuis Movviz (src/lib/ai/types.ts + providers.ts) —
 * même logique de rotation de clés + repli entre fournisseurs, même choix
 * de fournisseurs (tous avec un palier gratuit exploitable). Invisible tant
 * que `enabled` n'est pas explicitement activé (même règle que Movviz).
 */

export type AiProviderId = "mistral" | "openrouter" | "gemini";

export interface AiProviderKey {
  id: string;
  key: string;
}

export interface AiProviderConfig {
  model: string;
  keys: AiProviderKey[];
}

export interface AiConfig {
  enabled: boolean;
  /** Premier fournisseur tenté à chaque requête. */
  primary: AiProviderId;
  /** Si true, un fournisseur en échec (quota/erreur) bascule sur le suivant. */
  fallback: boolean;
  providers: Record<AiProviderId, AiProviderConfig>;
}

export const AI_PROVIDER_ORDER: AiProviderId[] = ["mistral", "openrouter", "gemini"];

export const DEFAULT_AI_CONFIG: AiConfig = {
  enabled: false,
  primary: "mistral",
  fallback: true,
  providers: {
    mistral: { model: "mistral-small-latest", keys: [] },
    openrouter: { model: "deepseek/deepseek-chat", keys: [] },
    gemini: { model: "gemini-2.5-flash-lite", keys: [] },
  },
};

/** Où obtenir une clé pour chaque fournisseur — affiché tel quel dans les réglages. */
export const AI_PROVIDER_INFO: Record<AiProviderId, { label: string; keyUrl: string; freeTier: string }> = {
  mistral: {
    label: "Mistral",
    keyUrl: "https://console.mistral.ai/api-keys",
    freeTier: "Palier gratuit disponible (La Plateforme)",
  },
  openrouter: {
    label: "OpenRouter",
    keyUrl: "https://openrouter.ai/keys",
    freeTier: "Plusieurs modèles gratuits/faible coût disponibles",
  },
  gemini: {
    label: "Google Gemini",
    keyUrl: "https://aistudio.google.com/apikey",
    freeTier: "Palier gratuit généreux (Google AI Studio)",
  },
};
