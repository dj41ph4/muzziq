import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";
import { DEFAULT_AI_CONFIG, type AiConfig } from "./types";

const FILE = dataFile("ai-config.json");

export function getAiConfig(): AiConfig {
  const stored = readJsonCached<Partial<AiConfig>>(FILE, {});
  return {
    ...DEFAULT_AI_CONFIG,
    ...stored,
    providers: { ...DEFAULT_AI_CONFIG.providers, ...(stored.providers ?? {}) },
  };
}

export function updateAiConfig(patch: Partial<AiConfig>): AiConfig {
  const next = { ...getAiConfig(), ...patch };
  writeJsonCached(FILE, next);
  return next;
}
