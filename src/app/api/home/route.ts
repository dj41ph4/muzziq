import { NextResponse } from "next/server";
import { getHomeRows } from "@/lib/recommendations/deterministicEngine";

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({ rows: await getHomeRows() });
}
