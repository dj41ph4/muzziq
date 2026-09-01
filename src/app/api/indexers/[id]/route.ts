import { NextResponse } from "next/server";
import { removeIndexer } from "@/lib/acquisition/indexers/store";

export const dynamic = "force-dynamic";

export async function DELETE(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const removed = removeIndexer(id);
  if (!removed) return NextResponse.json({ error: "Introuvable" }, { status: 404 });
  return NextResponse.json({ ok: true });
}
