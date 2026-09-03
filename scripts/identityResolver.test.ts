import { test } from "node:test";
import assert from "node:assert/strict";
import { trackMatchConfidence, bestMatch, RESOLUTION_CONFIDENCE_THRESHOLD } from "@/lib/identity/resolver";

test("titre/artiste identiques, durée proche — match au-dessus du seuil", () => {
  const confidence = trackMatchConfidence(
    { title: "Numb", artist: "Linkin Park", durationSeconds: 187 },
    { title: "Numb", artist: "Linkin Park", durationSeconds: 186 }
  );
  assert.ok(confidence >= RESOLUTION_CONFIDENCE_THRESHOLD, `confiance trop basse: ${confidence}`);
});

test("même titre, artiste différent — jamais fusionné (INTERDIT 7)", () => {
  const confidence = trackMatchConfidence(
    { title: "Numb", artist: "Linkin Park", durationSeconds: 187 },
    { title: "Numb", artist: "Usher", durationSeconds: 187 }
  );
  assert.ok(confidence < RESOLUTION_CONFIDENCE_THRESHOLD, `un artiste différent ne doit jamais atteindre le seuil, obtenu ${confidence}`);
});

test("remaster : même titre/artiste, durée différente — sous le seuil, jamais fusionné à tort", () => {
  const confidence = trackMatchConfidence(
    { title: "Numb", artist: "Linkin Park", durationSeconds: 187 },
    { title: "Numb - 2003 Remaster", artist: "Linkin Park", durationSeconds: 210 }
  );
  assert.ok(confidence < RESOLUTION_CONFIDENCE_THRESHOLD, `un remaster avec une durée très différente ne doit pas être fusionné, obtenu ${confidence}`);
});

test("live : même titre/artiste, durée très différente — sous le seuil", () => {
  const confidence = trackMatchConfidence(
    { title: "Numb", artist: "Linkin Park", durationSeconds: 187 },
    { title: "Numb (Live)", artist: "Linkin Park", durationSeconds: 260 }
  );
  assert.ok(confidence < RESOLUTION_CONFIDENCE_THRESHOLD, `un live avec une durée très différente ne doit pas être fusionné, obtenu ${confidence}`);
});

test("feat. artiste — le titre reste comparable malgré l'annotation", () => {
  const confidence = trackMatchConfidence(
    { title: "Numb / Encore", artist: "Linkin Park", durationSeconds: 205 },
    { title: "Numb / Encore", artist: "Linkin Park", durationSeconds: 205 }
  );
  assert.ok(confidence >= RESOLUTION_CONFIDENCE_THRESHOLD, `confiance trop basse: ${confidence}`);
});

test("ISRC identique — certitude (1.0), même si le titre diffère légèrement", () => {
  const confidence = trackMatchConfidence(
    { title: "Numb", artist: "Linkin Park", isrc: "USWB10300474" },
    { title: "Numb (Explicit)", artist: "Linkin Park Feat. X", isrc: "usw-b1-03-00474" }
  );
  assert.equal(confidence, 1, `un ISRC identique doit être une certitude absolue, obtenu ${confidence}`);
});

test("ISRC différent — jamais fusionné même si titre/artiste/durée collent parfaitement", () => {
  const confidence = trackMatchConfidence(
    { title: "Numb", artist: "Linkin Park", durationSeconds: 187, isrc: "USWB10300474" },
    { title: "Numb", artist: "Linkin Park", durationSeconds: 187, isrc: "USWB10300999" }
  );
  assert.equal(confidence, 0, `un ISRC différent doit primer sur un score textuel parfait, obtenu ${confidence}`);
});

test("bestMatch : jamais forcé sous le seuil parmi plusieurs candidats faibles", () => {
  const candidates = [
    { id: "a", title: "Numb (Live)", artist: "Linkin Park", durationSeconds: 260 },
    { id: "b", title: "Numb Remix", artist: "Linkin Park", durationSeconds: 300 },
  ];
  const match = bestMatch({ title: "Numb", artist: "Linkin Park", durationSeconds: 187 }, candidates);
  assert.equal(match, undefined, "aucun candidat ne doit être retenu sous le seuil de confiance");
});
