import { NextResponse } from "next/server";
import { getIndexer, updateIndexer } from "@/lib/acquisition/indexers/store";
import { testIndexer } from "@/lib/acquisition/indexers/torznabClient";

export const dynamic = "force-dynamic";

export async function POST(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const indexer = getIndexer(id);
  if (!indexer) return NextResponse.json({ error: "Introuvable" }, { status: 404 });

  const result = await testIndexer(indexer);
  updateIndexer(id, { lastTest: { ok: result.ok, at: Date.now(), detail: result.detail }, caps: result.caps });
  return NextResponse.json(result);
}
