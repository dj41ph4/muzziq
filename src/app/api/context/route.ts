import { NextResponse } from "next/server";
import { buildUnifiedUserContextSnapshot, formatUnifiedUserContext } from "@/lib/userContext/query";
import { getUserContextHealth } from "@/lib/userContext/database";
import { DEFAULT_USER_ID } from "@/lib/userContext/types";

export const dynamic = "force-dynamic";

export async function GET() {
  const snapshot = await buildUnifiedUserContextSnapshot(DEFAULT_USER_ID);
  return NextResponse.json({
    health: await getUserContextHealth(),
    snapshot,
    promptText: formatUnifiedUserContext(snapshot),
  });
}
