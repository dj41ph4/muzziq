import { dataFile } from "@/lib/config";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";

/**
 * provider_mappings (plan §67) : permet de changer/ajouter un provider sans
 * jamais toucher aux IDs MuzziQ. Un videoId YouTube ne devient JAMAIS un ID
 * canonique — il vit uniquement ici (INTERDIT 2).
 */

const FILE = dataFile("provider-mappings.json");

export interface ProviderMapping {
  entityType: "recording";
  entityId: string;
  provider: string;
  externalId: string;
}

function loadAll(): ProviderMapping[] {
  return readJsonCached<ProviderMapping[]>(FILE, []);
}

function saveAll(mappings: ProviderMapping[]): void {
  writeJsonCached(FILE, mappings);
}

export function findRecordingIdByProvider(provider: string, externalId: string): string | undefined {
  return loadAll().find((m) => m.provider === provider && m.externalId === externalId)?.entityId;
}

export function addMapping(mapping: ProviderMapping): void {
  const all = loadAll();
  const exists = all.some(
    (m) => m.entityType === mapping.entityType && m.provider === mapping.provider && m.externalId === mapping.externalId
  );
  if (!exists) {
    all.push(mapping);
    saveAll(all);
  }
}

export function listMappingsForRecording(recordingId: string): ProviderMapping[] {
  return loadAll().filter((m) => m.entityId === recordingId);
}
