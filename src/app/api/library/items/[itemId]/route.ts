import { NextResponse } from "next/server";
import { removeLibraryItem } from "@/lib/library/libraryItemsStore";

export const dynamic = "force-dynamic";

export async function DELETE(_req: Request, { params }: { params: Promise<{ itemId: string }> }) {
  const { itemId } = await params;
  const removed = removeLibraryItem(itemId);
  if (!removed) return NextResponse.json({ error: "Introuvable" }, { status: 404 });
  return NextResponse.json({ ok: true });
}
