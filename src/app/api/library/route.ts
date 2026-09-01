import { NextResponse } from "next/server";
import { listMediaFiles } from "@/lib/library/mediaFilesStore";

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({ files: listMediaFiles() });
}
